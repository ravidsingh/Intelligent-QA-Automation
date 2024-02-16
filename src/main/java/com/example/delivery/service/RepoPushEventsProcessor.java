package com.example.delivery.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.Configuration;
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

	private static final String AUTHORIZATION = "Authorization";

	private static final String REFS_HEADS_MAIN = "refs/heads/main";

	@Autowired
	RestTemplate restTemplate;

	@Value("${test-tool-url}")
	private String jiraUrl;

	@Value("${jenkins-url}")
	private String jenkinsUrl;

	@Value("${jira-key}")
	private String jiraKey;

	Configuration configuration = Configuration.defaultConfiguration().setOptions(Option.SUPPRESS_EXCEPTIONS);

	public ResponseEntity<String> processPushEvent(String pushEvent) {
		String branchNameFilterRegex = "$['ref']";
		String branchName = JsonPath.using(configuration).parse(pushEvent).read(branchNameFilterRegex);
		List<String> testCaseId = null;
		if (null != branchName && REFS_HEADS_MAIN.equals(branchName)) {
			log.info("Processing github push event.");
			try {
				String commitFilterRegex = "$['commits'][0]['message']";
				String message = JsonPath.using(configuration).parse(pushEvent).read(commitFilterRegex);
				Pattern jiraIdPattern = Pattern.compile("WMOD-[0-9]+", Pattern.CASE_INSENSITIVE);
				Matcher jiraIdmatcher = jiraIdPattern.matcher(message);
				if (jiraIdmatcher.find()) {
					var jiraId = jiraIdmatcher.group();
					log.info("Changes merged to main branch for Jira Id: {}", jiraId);

					testCaseId = fetchJiraTestCases(jiraId);
					String testIds = String.join(" or ", testCaseId);
					log.info("Excecuting Test cases: [{}] for feature : {}", testIds, jiraId);
					return ResponseEntity.ok(testIds);
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

	private List<String> fetchJiraTestCases(String jiraId) {
		ResponseEntity<String> response = getJiraApiResponse(jiraId);
		List<String> testSuiteId = extractJiraIdFromResponse(response, "WQAMOD-[0-9]+");

		log.info("Retrived jira testsuit ids {}", testSuiteId);
		List<String> testCaseIds = new ArrayList<>();
		testSuiteId.forEach(jiraKey -> {
			ResponseEntity<String> JiraResponse = getJiraApiResponse(jiraKey);
			extractJiraIdFromResponse(JiraResponse, "WQST-[0-9]+").forEach(value -> testCaseIds.add(value));
		});
		log.info("testcase tags: {}", testCaseIds);
		return testCaseIds;
	}

	private List<String> extractJiraIdFromResponse(ResponseEntity<String> response, String jiraIdRegex) {
		String filter_Regex = "$['fields']['issuelinks'][*]['outwardIssue']['key']";
		List<String> testSuiteId = JsonPath.using(configuration).parse(response.getBody()).read(filter_Regex);
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

	private Map<String, String> getJiraParam(String value) {
		Map<String, String> uriVariables = new HashMap<>();
		uriVariables.put("jiraId", value);
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
}
