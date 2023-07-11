package com.example.delivery.model;

import java.util.ArrayList;
import java.util.List;



public class ArtifactInformation {

	private List<String> testCases = new ArrayList<>();
	
	private List<String> packageList = new ArrayList<>();

	public List<String> getPackageList() {
		return packageList;
	}

	public void setPackageList(List<String> packageList) {
		this.packageList = packageList;
	}

	public List<String> getTestCases() {
		return testCases;
	}

	public void setTestCases(List<String> testCases) {
		this.testCases = testCases;
	}

	@Override
	public String toString() {
		return "ArtifactInformation [testCases=" + testCases + "]";
	}

}