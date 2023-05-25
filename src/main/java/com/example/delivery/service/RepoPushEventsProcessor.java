package com.example.delivery.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
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
	private String url;
	
	@Value("${jenkins-url}")
	private String jenkinsUrl;

	private static final String HEAD_COMMIT = "head_commit";
	private static final String REF = "ref";
	
	public ResponseEntity<String> processPushEvent(String pushEvent) {
		
		ObjectMapper objectMapper = new ObjectMapper();
		// System.out.println("###### Webhook #####" + requestBody);
		JsonParser parser = JsonParserFactory.getJsonParser();
		Map<String, Object> req = parser.parseMap(pushEvent);
		ResponseEntity<String> response = null;
		String result = null;
		if (null != req.get(REF) && req.get(REF).equals(REFS_HEADS_MAIN)) {

			/*
			 * System.out.println(objectMapper.writerWithDefaultPrettyPrinter()
			 * .writeValueAsString(req));
			 */
			try {
				Map<String, Object> commitObj = parser.parseMap(objectMapper.writeValueAsString(req.get(HEAD_COMMIT)));
				String[] lines = commitObj.get(PR_MESSAGE).toString().split(NEXT_LINE_DELIMETER);
				 Pattern pattern = Pattern.compile("IQE-[0-9]+", Pattern.CASE_INSENSITIVE);
				  Matcher matcher = pattern.matcher(lines[lines.length - 1]);
				 
				if(matcher.find()) {
				var jiraId = matcher.group();
				System.out.println("Changes merged to main branch for Jira Id: " + jiraId);
				HttpHeaders header = new HttpHeaders();
				header.add("Authorization",
						"Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJjb250ZXh0Ijp7ImJhc2VVcmwiOiJodHRwczovL2lxZXBvYy5hdGxhc3NpYW4ubmV0IiwidXNlciI6eyJhY2NvdW50SWQiOiI3MTIwMjA6YmQ2NmE1MjMtZDMxYi00OWQwLWExNzktMjU1MTEwNTRlODZhIn19LCJpc3MiOiJjb20ua2Fub2FoLnRlc3QtbWFuYWdlciIsInN1YiI6Ijk5NWQ0ZGRhLTJmNTktMzE3Ny1hMmYzLTliYzIxNWI2OGZlMSIsImV4cCI6MTcxMjgyOTk0NiwiaWF0IjoxNjgxMjkzOTQ2fQ.ZbORcKZOBdYsFFdsFES4ABMS-KAHOFQGEWBJOXMLXLM");
				response = restTemplate.exchange(url, HttpMethod.GET,
						new HttpEntity<>(header), String.class, getParam(jiraId));
				List<Object> zephyrResponse = parser.parseList(response.getBody());
				var testcase = getTestCaseInfo(objectMapper, zephyrResponse);
				var testCaseId = testcase.stream().map(val -> parser.parseMap(val).get("key")).collect(Collectors.toList());
				System.out.println("Excecuting Test cases:" + testCaseId+" in user story "+jiraId);
				Process process;
				process = Runtime.getRuntime().exec("curl -I "+jenkinsUrl);
				InputStream stream = process.getInputStream();
				result = IOUtils.toString(stream, StandardCharsets.UTF_8);
				} else {
					System.out.println("No Jira Id found in the PR message. Can't process further. Please provide Jira-id in the PR message.");
					return ResponseEntity.unprocessableEntity().body("No Jira Id found in the PR message. Can't process further. Please provide Jira-id in the PR message.");
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return ResponseEntity.unprocessableEntity().body(e.getMessage());
			}
			
		}
		return ResponseEntity.ok("Trigger Successful : " + result);
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

	private Map<String, String> getParam(String value) {
		Map<String, String> uriVariables = new HashMap<>();
		uriVariables.put("jiraId", value);
		return uriVariables;
	}
}
