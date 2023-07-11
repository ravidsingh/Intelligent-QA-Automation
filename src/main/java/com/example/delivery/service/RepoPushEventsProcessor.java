package com.example.delivery.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.delivery.model.ArtifactInformation;
import com.example.delivery.model.QtestRequirement;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RepoPushEventsProcessor {

	private static final String NEXT_LINE_DELIMETER = "\n";

	private static final String PR_MESSAGE = "message";

	private static final String REFS_HEADS_MAIN = "refs/heads/main";

	@Autowired
	RestTemplate restTemplate;

	@Value("${test-tool-url}")
	private String zephyreUrl;

	@Value("${qtest-url}")
	private String qtestUrl;

	@Value("${jenkins-url}")
	private String jenkinsUrl;

	@Value("${use-qtest}")
	private boolean useQtest;

	@Value("${projectId}")
	private String projectId;

	@Value("${zephyre-key}")
	private String zephyreKey;

	@Value("${qtest-key}")
	private String qtestKey;

	private static final String HEAD_COMMIT = "head_commit";
	private static final String REF = "ref";

	public ResponseEntity<String> processPushEvent(String pushEvent) {

		ObjectMapper objectMapper = new ObjectMapper();
		List<String> testCaseId = null;
		JsonParser parser = JsonParserFactory.getJsonParser();
		Map<String, Object> req = parser.parseMap(pushEvent);
		ResponseEntity<?> result = null;
		var targetApp = new HashSet<String>(); 
		if (null != req.get(REF) && req.get(REF).equals(REFS_HEADS_MAIN)) {
			log.info("Processing github push event.");
			try {
				Map<String, Object> commitObj = parser.parseMap(objectMapper.writeValueAsString(req.get(HEAD_COMMIT)));
				String[] lines = commitObj.get(PR_MESSAGE).toString().split(NEXT_LINE_DELIMETER);
				Pattern jiraIdPattern = Pattern.compile("IQE-[0-9]+", Pattern.CASE_INSENSITIVE);
				String prMessage = lines[lines.length - 1];
				Matcher jiraIdmatcher = jiraIdPattern.matcher(prMessage);
				if (jiraIdmatcher.find()) {
					var jiraId = jiraIdmatcher.group();
					log.info("Changes merged to main branch for Jira Id: {}", jiraId);

					if (prMessage.contains("#QTest")) {
						testCaseId = useQtest ? fetchQtestTestCases(parser, jiraId, objectMapper)
								: fetchZephyrTestCases(parser, jiraId, objectMapper);
						log.info("Excecuting Test cases: {} for user story: {}", testCaseId, jiraId);
					}

					if (prMessage.contains("#Regression")) {

						var listForAddedFiles = convertToList(commitObj.get("added"), objectMapper);
						var listForDeletedFiles = convertToList(commitObj.get("removed"), objectMapper);
						var listForUpdatedFiles = convertToList(commitObj.get("modified"), objectMapper);

						  var moduleListForAdd = listForAddedFiles.stream().map(val -> { var str =
						  Objects.toString(val, null); return val.toString().substring(0,
						  val.toString().indexOf("/")); }) .collect(Collectors.toSet());
						  
						  var moduleListForDelete = listForDeletedFiles.stream() .map(val ->
						  val.toString().substring(0, val.toString().indexOf("/")))
						  .collect(Collectors.toSet());
						  
						  var moduleListForUpdate = listForUpdatedFiles.stream() .map(val ->
						  val.toString().substring(0, val.toString().indexOf("/")))
						  .collect(Collectors.toSet());
						  
						  targetApp.addAll(moduleListForAdd);
						  targetApp.addAll(moduleListForDelete);
						  targetApp.addAll(moduleListForUpdate);
						  log.info("Triggered automation job for module: {}", targetApp);
					}
					
					HttpHeaders headers = new HttpHeaders();

					headers.setContentType(MediaType.APPLICATION_JSON);
					headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
					URI uri = new URI("https://3dd3-49-36-91-7.in.ngrok.io/jenkins/remote/invoke");

					ArtifactInformation request = new ArtifactInformation();
					request.setTestCases(testCaseId);
					request.setPackageList(new ArrayList<>(targetApp));
					
					HttpEntity<ArtifactInformation> httpEntity = new HttpEntity<>(request, headers);
					result = restTemplate.postForEntity(uri, httpEntity, Object.class);
				} else {
					log.info(
							"No Jira Id found in the PR message. Can't process further. Please provide Jira-id in the PR message.");
					return ResponseEntity.unprocessableEntity().body(
							"No Jira Id found in the PR message. Can't process further. Please provide Jira-id in the PR message.");
				}
			} catch (Exception e) {
				log.error("Exception occured while processing request.", e);
				return ResponseEntity.status(result.getStatusCode()).build();
			}

		}
		return ResponseEntity.ok("Trigger Successful ");
	}

	private List<String> convertToList(Object commitObj, ObjectMapper mapper) {
		var listForAddedFiles = mapper.convertValue(commitObj, List.class);
		List<String> tempList = new ArrayList<>();
		for (Object obj : listForAddedFiles) {
			tempList.add(String.valueOf(obj));
		}
		return tempList;
	}

	private List<String> fetchZephyrTestCases(JsonParser parser, String jiraId, ObjectMapper objectMapper)
			throws JsonProcessingException {
		ResponseEntity<String> response;
		HttpHeaders header = new HttpHeaders();
		header.add("Authorization", zephyreKey);
		response = restTemplate.exchange(zephyreUrl, HttpMethod.GET, new HttpEntity<>(header), String.class,
				getZephyreParam(jiraId));
		List<Object> zephyrResponse = parser.parseList(response.getBody());
		var testcase = getTestCaseInfo(objectMapper, zephyrResponse);
		var testCaseId = testcase.stream().map(val -> parser.parseMap(val).get("key").toString())
				.collect(Collectors.toList());
		return testCaseId;
	}

	private List<String> fetchQtestTestCases(JsonParser parser, String jiraId, ObjectMapper objectMapper)
			throws JsonProcessingException {
		ResponseEntity<?> response;
		HttpHeaders header = new HttpHeaders();
		header.add("Authorization", qtestKey);
		response = restTemplate.exchange(qtestUrl, HttpMethod.GET, new HttpEntity<>(header), Object.class,
				getqtestParam(projectId));
		var qtestResponse = (List<Object>)(response.getBody());
		//var reqList = objectMapper.writeValueAsString(header)
		return getTestCaseInfo(objectMapper, qtestResponse, parser, jiraId);
		//return null;
	}

	private List<String> getTestCaseInfo(ObjectMapper objectMapper, List<Object> zephyrResponse)
			throws JsonProcessingException {
		var testcase = zephyrResponse.stream().map(val -> {
			String data = null;
			try {
				data = objectMapper.writeValueAsString(val);
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
			return data;
		}).collect(Collectors.toList());
		return testcase;
	}

	private List<String> getTestCaseInfo(ObjectMapper objectMapper, List<Object> qTestResponse, JsonParser parser,
			String issueId) throws JsonProcessingException {

		var userStories = qTestResponse.stream().map(val1 -> objectMapper.convertValue(val1, Map.class))
				.map(key -> key.get("requirements")).collect(Collectors.toList());

		List<QtestRequirement> reqList = new ArrayList<>();

		userStories.stream().forEach(us -> {
			var list = objectMapper.convertValue(us, new TypeReference<List<Object>>() {
			});
			list.stream().forEach(li -> {
				reqList.add(objectMapper.convertValue(li, QtestRequirement.class));
			});
		});

		var testCases = reqList.stream()
				.filter(li -> li.getName().contains(issueId) && Objects.nonNull(li.getLinkedTestcases()))
				.map(arg -> arg.getTestcases()).collect(Collectors.toList());
		return testCases.stream().filter(Objects::nonNull)
				.flatMap(list -> Arrays.asList(list.toString().split(", ")).stream()).collect(Collectors.toList());
	}

	private Map<String, String> getZephyreParam(String value) {
		Map<String, String> uriVariables = new HashMap<>();
		uriVariables.put("jiraId", value);
		return uriVariables;
	}

	private Map<String, String> getqtestParam(String value) {
		Map<String, String> uriVariables = new HashMap<>();
		uriVariables.put("projectId", value);
		return uriVariables;
	}

}
