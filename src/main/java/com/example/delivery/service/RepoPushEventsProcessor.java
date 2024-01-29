package com.example.delivery.service;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.cdancy.jenkins.rest.JenkinsClient;
import com.cdancy.jenkins.rest.domain.common.IntegerResponse;
import com.cdancy.jenkins.rest.domain.system.SystemInfo;
import com.example.delivery.model.QtestRequirement;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Criteria;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.Filter;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@AllArgsConstructor
@NoArgsConstructor
public class RepoPushEventsProcessor {

	private static final String KEY_FIELD = "key";

	private static final String AUTHORIZATION = "Authorization";

	private static final String NEXT_LINE_DELIMETER = "\n";

	private static final String PR_MESSAGE = "message";

	private static final String REFS_HEADS_MAIN = "refs/heads/main";

	@Autowired
	RestTemplate restTemplate;

	@Value("${test-tool-url}")
	private String jiraUrl;


	@Value("${jenkins-url}")
	private String jenkinsUrl;


	@Value("${jira-key}")
	private String jiraKey;

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
				Pattern jiraIdPattern = Pattern.compile("WMOD-[0-9]+", Pattern.CASE_INSENSITIVE);
				String prMessage = lines[lines.length - 1];
				Matcher jiraIdmatcher = jiraIdPattern.matcher(prMessage);
				if (jiraIdmatcher.find()) {
					var jiraId = jiraIdmatcher.group();
					log.info("Changes merged to main branch for Jira Id: {}", jiraId);

					//if (isDeployDone()) {
						testCaseId = fetchJiraTestCases(parser, jiraId, objectMapper);
						String testIds = String.join(" or ", testCaseId);
						log.info("Excecuting Test cases: [{}] for feature : {}", testIds, jiraId);
						return ResponseEntity.ok(testIds);
						/*
						 * String command = getJenkinsBuildWithParamUrl("test1", testIds); Process
						 * process = Runtime.getRuntime().exec(command); var responseMsg =
						 * process.getInputStream().read() > 0 ? "Successfully called Jenkin Job" :
						 * "Error occurred while calling Jenkin job"; log.info(responseMsg);
						 */
					//}

					/*
					 * if (prMessage.contains("#Regression")) {
					 * 
					 * var listForAddedFiles = convertToList(commitObj.get("added"), objectMapper);
					 * var listForDeletedFiles = convertToList(commitObj.get("removed"),
					 * objectMapper); var listForUpdatedFiles =
					 * convertToList(commitObj.get("modified"), objectMapper);
					 * 
					 * var moduleListForAdd = listForAddedFiles.stream().map(val -> { var str =
					 * Objects.toString(val, null); return val.toString().substring(0,
					 * val.toString().indexOf("/")); }).collect(Collectors.toSet());
					 * 
					 * var moduleListForDelete = listForDeletedFiles.stream() .map(val ->
					 * val.toString().substring(0, val.toString().indexOf("/")))
					 * .collect(Collectors.toSet());
					 * 
					 * var moduleListForUpdate = listForUpdatedFiles.stream() .map(val ->
					 * val.toString().substring(0, val.toString().indexOf("/")))
					 * .collect(Collectors.toSet());
					 * 
					 * targetApp.addAll(moduleListForAdd); targetApp.addAll(moduleListForDelete);
					 * targetApp.addAll(moduleListForUpdate); for (String module : targetApp) {
					 * String command = getJenkinsBuildUrl(module); Process process =
					 * Runtime.getRuntime().exec(command); }
					 * log.info("Triggered automation job for module: {}", targetApp); }
					 */
				} else {
					log.info(
							"No Jira Id found in the PR message. Can't process further. Please provide Jira-id in the PR message.");
					return ResponseEntity.unprocessableEntity().body(
							"No Jira Id found in the PR message. Can't process further. Please provide Jira-id in the PR message.");
				}
			} catch (Exception e) {
				log.error("Exception occured while processing request.", e);
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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

	private List<String> fetchJiraTestCases(JsonParser parser, String jiraId, ObjectMapper objectMapper)
			throws JsonProcessingException {
		ResponseEntity<String> response = getJiraApiResponse(jiraId);
		List<String> testSuiteId = extractJiraIdFromResponse(response, "WQAMOD-[0-9]+");
		
		log.info("Retrived jira testsuit ids {}", testSuiteId);
		List<String> testCaseIds = new ArrayList<>();
		testSuiteId.forEach(jiraKey -> { 
			ResponseEntity<String> JiraResponse = getJiraApiResponse(jiraKey);
			extractJiraIdFromResponse(JiraResponse, "WQST-[0-9]+")
			.forEach(value-> testCaseIds.add(value));
			});
		log.info("testcase tags: {}", testCaseIds);
		return testCaseIds;
	}

	private List<String> extractJiraIdFromResponse(ResponseEntity<String> response, String jiraIdRegex) {
		String filter_Regex = "$['fields']['issuelinks'][*]['outwardIssue']['key']";
		/*
		 * Filter testSuiteFilter = Filter
		 * .filter(Criteria.where(KEY_FIELD).regex(Pattern.compile(jiraIdRegex,
		 * Pattern.CASE_INSENSITIVE)));
		 */
		Configuration configuration = Configuration.defaultConfiguration().setOptions(Option.SUPPRESS_EXCEPTIONS);
		List<String> testSuiteId = JsonPath.using(configuration).parse(response.getBody())
				.read(filter_Regex);
		return testSuiteId.stream().filter(item -> item.matches(jiraIdRegex)).collect(Collectors.toList());
	}

	private ResponseEntity<String> getJiraApiResponse(String jiraId) {
		ResponseEntity<String> response;
		HttpHeaders header = new HttpHeaders();
		header.add(AUTHORIZATION, jiraKey);
		response = restTemplate.exchange(jiraUrl, HttpMethod.GET, new HttpEntity<>(header), String.class,
				getJiraParam(jiraId));
		return response;
	}

	/*
	 * private List<String> fetchQtestTestCases(JsonParser parser, String jiraId,
	 * ObjectMapper objectMapper) throws JsonProcessingException { ResponseEntity<?>
	 * response; HttpHeaders header = new HttpHeaders(); header.add("Authorization",
	 * qtestKey); response = restTemplate.exchange(qtestUrl, HttpMethod.GET, new
	 * HttpEntity<>(header), Object.class, getqtestParam(projectId)); var
	 * qtestResponse = (List<Object>) (response.getBody()); // var reqList =
	 * objectMapper.writeValueAsString(header) return getTestCaseInfo(objectMapper,
	 * qtestResponse, parser, jiraId); // return null; }
	 */

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

	private Map<String, String> getJiraParam(String value) {
		Map<String, String> uriVariables = new HashMap<>();
		uriVariables.put("jiraId", value);
		return uriVariables;
	}

	private Map<String, String> getqtestParam(String value) {
		Map<String, String> uriVariables = new HashMap<>();
		uriVariables.put("projectId", value);
		return uriVariables;
	}

	private String getJenkinsBuildWithParamUrl(String jobName, String buildParams) {
		String jobUrl = "curl -I ".concat(jenkinsUrl).concat("/").concat(jobName).concat("/")
				.concat("buildWithParameters?token=").concat("ravideep").concat("&").concat("testsuite").concat("=")
				+ (buildParams).concat(" ").concat("--user").concat(" ").concat("root").concat(":").concat("root");
		log.info("job url {}", jobUrl);
		return jobUrl;

	}

	private String getJenkinsBuildUrl(String jobName) {
		String jobUrl = "curl -I ".concat(jenkinsUrl).concat("/").concat(jobName).concat("/").concat("build?token=")
				.concat("ravideep").concat(" ").concat("--user").concat(" ").concat("root").concat(":").concat("root");
		return jobUrl;
	}

	private boolean isDeployDone() throws Exception {
		int status;
		do {
			URL url = new URL("http://13.58.54.12/login");
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			Thread.sleep(120 * 1000);
			connection.connect();
			status = connection.getResponseCode();
			log.info("Code deployment status {}", status);
			System.out.println("Code deployment status " + status);
		} while (status != 200);
		return status == 200 ? true : false;
	}
}
