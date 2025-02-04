package edu.upc.llm;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import edu.upc.entity.LlmMessage;

public class LlmClient {
	private URI apiUrl;
	private String apiKey;
	private String model;

	private HttpClient httpClient;

	public LlmClient() {
		Properties properties = new Properties();
		try (InputStream inputStream = LlmClient.class.getClassLoader().getResourceAsStream("config.properties")) {
			if (inputStream == null) {
				throw new RuntimeException(
						"Could not find the configuration. If the application is run for the first time, you need to create it manually.");
			}
			properties.load(inputStream);
			this.apiUrl = new URI(properties.getProperty("api_url"));
			this.apiKey = properties.getProperty("api_key");
			this.model = properties.getProperty("model");
			this.httpClient = HttpClient.newHttpClient();

		} catch (Exception e) {
			throw new RuntimeException("Error reading config file: " + e.toString());
		}
	}

	public JSONObject callLlmJson(List<LlmMessage> messages, JSONObject jsonSchema,float temperature) {
		String response = this.callLlm(messages,
				Map.of("response_format", Map.of("type", "json_schema", "json_schema", jsonSchema)),temperature);

		JSONParser jsonParser = new JSONParser();
		try {
			return (JSONObject) jsonParser.parse(response);
		} catch (Exception e) {
			throw new RuntimeException("Error parsing LLM response: " + e.toString());
		}
	}

	@SuppressWarnings("unchecked")
	public String callLlm(List<LlmMessage> messages, Map<String, Object> extraParams, float temperature) {
		JSONObject jsonBody = new JSONObject();
		jsonBody.put("model", this.model);
		jsonBody.put("temperature", temperature);

		List<JSONObject> jsonMessages = new ArrayList<JSONObject>();
		for (LlmMessage message : messages) {
			jsonMessages.add(message.toJsonObject());
		}
		jsonBody.put("messages", jsonMessages);
		jsonBody.putAll(extraParams);

		// Create the request object
		HttpRequest request = HttpRequest.newBuilder().uri(this.apiUrl).header("Authorization", "Bearer " + this.apiKey)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(jsonBody.toString())).build();

		// Send the request
		try {
			HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {
				String bodyStr = response.body();
				JSONObject body = (JSONObject) new JSONParser().parse(bodyStr);
				JSONArray choices = (JSONArray) body.get("choices");

				if (choices.size() == 0) {
					throw new RuntimeException("No choices returned by LLM");
				}

				JSONObject firstChoice = (JSONObject) choices.get(0);
				if (!firstChoice.get("finish_reason").equals("stop")) {
					throw new RuntimeException("LLM did not finish generating a response");
				}

				JSONObject message = (JSONObject) firstChoice.get("message");
				if (message.containsKey("refusal") && message.get("refusal") != null) {
					throw new RuntimeException("LLM refused to generate a response: " + message.get("refusal"));
				}

				return (String) message.get("content");
			} else {
				throw new RuntimeException(
						"Error calling LLM: Status " + response.statusCode() + " " + response.body());
			}
		} catch (Exception e) {
			throw new RuntimeException("Error calling LLM: " + e.toString());
		}
	}

}
