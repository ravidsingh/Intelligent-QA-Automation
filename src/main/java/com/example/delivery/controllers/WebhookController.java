package com.example.delivery.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.example.delivery.service.RepoPushEventsProcessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

	@Autowired
	RestTemplate restTemplate;
	
	@Autowired
	RepoPushEventsProcessor eventProcessor;
	

	@PostMapping // http://localhost:8080/api/webhook
	public ResponseEntity<String> print(@RequestBody String requestBody)
			throws JsonMappingException, JsonProcessingException {
		
		return eventProcessor.processPushEvent(requestBody);
	}
}