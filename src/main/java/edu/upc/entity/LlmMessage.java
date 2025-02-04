package edu.upc.entity;

import org.json.simple.JSONObject;

public class LlmMessage {
	private String role;
	private String content;

	public LlmMessage(String role, String content) {
		this.role = role;
		this.content = content;
	}

	public String getRole() {
		return role;
	}

	public String getContent() {
		return content;
	}

	@SuppressWarnings("unchecked")
	public JSONObject toJsonObject() {
		JSONObject obj = new JSONObject();
		obj.put("role", role);
		obj.put("content", content);
		return obj;
	}
}
