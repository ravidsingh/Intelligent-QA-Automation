package com.example.delivery.controllers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

	private static final String NEXT_LINE_DELIMETER = "\n";

	private static final String PR_MESSAGE = "message";

	private static final String REFS_HEADS_MAIN = "refs/heads/main";

	@Autowired
	RestTemplate restTemplate;
	
	
	@Value("${test-tool-url}")
	private String url;

	private static final String HEAD_COMMIT = "head_commit";
	private static final String REF = "ref";

	@PostMapping // http://localhost:8080/api/webhook
	public ResponseEntity<String> print(@RequestBody String requestBody)
			throws JsonMappingException, JsonProcessingException {
		ObjectMapper objectMapper = new ObjectMapper();
		// System.out.println("###### Webhook #####" + requestBody);
		JsonParser parser = JsonParserFactory.getJsonParser();
		Map<String, Object> req = parser.parseMap(requestBody);
		ResponseEntity<String> response = null;
		if (null != req.get(REF) && req.get(REF).equals(REFS_HEADS_MAIN)) {

			/*
			 * System.out.println(objectMapper.writerWithDefaultPrettyPrinter()
			 * .writeValueAsString(req));
			 */

			Map<String, Object> commitObj = parser.parseMap(objectMapper.writeValueAsString(req.get(HEAD_COMMIT)));
			String[] lines = commitObj.get(PR_MESSAGE).toString().split(NEXT_LINE_DELIMETER);
			System.out.println("Changes merged to main branch for Jira Id: " + lines[lines.length - 1]);
			var jiraId = lines[lines.length - 1];
			HttpHeaders header = new HttpHeaders();
			header.add("Authorization",
					"Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJjb250ZXh0Ijp7ImJhc2VVcmwiOiJodHRwczovL2lxZXBvYy5hdGxhc3NpYW4ubmV0IiwidXNlciI6eyJhY2NvdW50SWQiOiI3MTIwMjA6YmQ2NmE1MjMtZDMxYi00OWQwLWExNzktMjU1MTEwNTRlODZhIn19LCJpc3MiOiJjb20ua2Fub2FoLnRlc3QtbWFuYWdlciIsInN1YiI6Ijk5NWQ0ZGRhLTJmNTktMzE3Ny1hMmYzLTliYzIxNWI2OGZlMSIsImV4cCI6MTcxMjgyOTk0NiwiaWF0IjoxNjgxMjkzOTQ2fQ.ZbORcKZOBdYsFFdsFES4ABMS-KAHOFQGEWBJOXMLXLM");
			response = restTemplate.exchange(url, HttpMethod.GET,
					new HttpEntity<>(header), String.class, getParam(jiraId));
			List<Object> zephyrResponse = parser.parseList(response.getBody());
			var testcase = getTestCaseInfo(objectMapper, zephyrResponse);
			var testCaseId = testcase.stream().map(val -> parser.parseMap(val).get("key")).collect(Collectors.toList());
			System.out.println("Test cases needs to ran :" + testCaseId);
			/*
			 * var testcaseIds = zephyrResponse.stream()
			 * .map(val->objectMapper.writeValueAsString(val))
			 * .map(data->parser.parseMap(data)) .map(map->map.get("key").toString())
			 * .collect(Collectors.toList()); System.out.println(testcaseIds);
			 */
			// System.out.println("List of files removed" + commitObj.get("removed"));
			// System.out.println("List of files modified" + commitObj.get("modified"));

			/*
			 * var listForAddedFiles = convertObjectToList(commitObj.get("added")); var
			 * listForDeletedFiles = convertObjectToList(commitObj.get("removed")); var
			 * listForUpdatedFiles = convertObjectToList(commitObj.get("modified"));
			 * 
			 * var moduleListForAdd = listForAddedFiles.stream() .map(val ->
			 * val.toString().substring(0,
			 * val.toString().indexOf("/"))).collect(Collectors.toSet());
			 * 
			 * var moduleListForDelete = listForDeletedFiles.stream() .map(val ->
			 * val.toString().substring(0,
			 * val.toString().indexOf("/"))).collect(Collectors.toSet());
			 * 
			 * var moduleListForUpdate = listForUpdatedFiles.stream() .map(val ->
			 * val.toString().substring(0,
			 * val.toString().indexOf("/"))).collect(Collectors.toSet());
			 * 
			 * var targetApp = new HashSet<String>(); targetApp.addAll(moduleListForAdd);
			 * targetApp.addAll(moduleListForDelete); targetApp.addAll(moduleListForUpdate);
			 * System.out.println("================= triggered automation job for module:" +
			 * targetApp); var headers = new HttpHeaders(); var requestEntity = new
			 * HttpEntity<>(headers);
			 * 
			 * 
			 * try { targetApp.stream().forEach(val -> { var response =
			 * restTemplate.exchange(buildUrl(), HttpMethod.POST, requestEntity,
			 * String.class, getParam(val)); System.out.println(response); }); } catch
			 * (Exception ex) { ex.printStackTrace(); }
			 */
			// targetApp.stream().forEach(val -> triggerJenkinsJob(val));

		}
		return response;
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

	private List<?> convertObjectToList(Object obj) {
		List<?> list = new ArrayList<>();
		if (obj.getClass().isArray()) {
			list = Arrays.asList((Object[]) obj);
		} else if (obj instanceof Collection) {
			list = new ArrayList<>((Collection<?>) obj);
		}
		return list;
	}

	private String buildUrl(String jiraId) {
		return "https://api.zephyrscale.smartbear.com/v2/issuelinks/" + jiraId + "/testcases";
	}

	private Map<String, String> getParam(String value) {
		Map<String, String> uriVariables = new HashMap<>();
		uriVariables.put("jiraId", value);
		return uriVariables;
	}

}