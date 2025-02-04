package edu.upc.utils;

import java.io.InputStream;
import java.util.Map;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import edu.upc.llm.LlmClient;

public class ResourcesHandler {
	public static JSONObject getJsonSchemaByName(String name) {
		try (InputStream inputStream = LlmClient.class.getClassLoader()
				.getResourceAsStream("schemas/" + name + ".json")) {
			String schema = new String(inputStream.readAllBytes(), "UTF-8");
			return (JSONObject) new JSONParser().parse(schema);
		} catch (Exception e) {
			throw new RuntimeException("Error reading schema file: " + e.toString());
		}
	}

	public static String getPromptByName(String name) {
		return ResourcesHandler.getPromptByName(name, Map.of());
	}

	public static String getPromptByName(String name, Map<String, String> args) {
		try (InputStream inputStream = LlmClient.class.getClassLoader()
				.getResourceAsStream("prompts/" + name + ".txt")) {
			String prompt = new String(inputStream.readAllBytes(), "UTF-8");
			for (Map.Entry<String, String> entry : args.entrySet()) {
				prompt = prompt.replace("{{" + entry.getKey() + "}}", entry.getValue());
			}
			return prompt;
		} catch (Exception e) {
			throw new RuntimeException("Error reading prompt file: " + e.toString());
		}
	}

	public static String getTextByName(String name) {
		try (InputStream inputStream = LlmClient.class.getClassLoader().getResourceAsStream("texts/" + name + ".txt")) {
			return new String(inputStream.readAllBytes(), "UTF-8");
		} catch (Exception e) {
			throw new RuntimeException("Error reading text file: " + e.toString());
		}
	}
}
