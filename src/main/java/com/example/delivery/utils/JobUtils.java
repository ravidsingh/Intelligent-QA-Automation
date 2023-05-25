package com.example.delivery.utils;

import java.io.InputStream;


public class JobUtils {

	private final static String remoteJobUrl = "curl -I http://localhost:8080/job/%s/build?token=vishalarora --user root:root";

	public static String getJobUrl(String jobType) {
		try {
			Process proc = Runtime.getRuntime().exec("java -jar HeadlessAutomationJar.jar");
			proc.waitFor();
			// Then retreive the process output
			InputStream in = proc.getInputStream();
			InputStream err = proc.getErrorStream();

			byte b[] = new byte[in.available()];
			in.read(b, 0, b.length);
			System.out.println(new String(b));

			byte c[] = new byte[err.available()];
			err.read(c, 0, c.length);
			System.out.println(new String(c));
		} catch (Exception e) {
			e.printStackTrace();
		}

		return String.format(remoteJobUrl, jobType);
	}

}
