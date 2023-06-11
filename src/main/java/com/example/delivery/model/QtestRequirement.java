package com.example.delivery.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QtestRequirement {
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getTestcases() {
		return testcases;
	}
	public void setTestcases(String testcases) {
		this.testcases = testcases;
	}
	public String getLinkedTestcases() {
		return linkedTestcases;
	}
	public void setLinkedTestcases(String linkedTestcases) {
		this.linkedTestcases = linkedTestcases;
	}
	@JsonProperty("name")
	private String name;
	@JsonProperty("id")
	private String id;
	@JsonProperty("testcases")
	private String testcases;
	@JsonProperty("linked-testcases")
	private String linkedTestcases;

}
