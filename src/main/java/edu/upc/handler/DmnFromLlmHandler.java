package edu.upc.handler;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import edu.upc.entity.LlmMessage;
import edu.upc.llm.LlmClient;
import edu.upc.utils.ResourcesHandler;

public class DmnFromLlmHandler {
	private LlmClient llmClient;
	private String fileName;

	public DmnFromLlmHandler(LlmClient llmClient, String fileName) {
		this.llmClient = llmClient;
		this.fileName = fileName;
	}

	public JSONObject extractDMN(String textToParse, boolean useCache, float temperature) throws Exception {
		String systemPrompt = "dmn_extractor_system";
		String userPrompt = "dmn_extractor_user";
		Map<String, String> promptArgs = Map.of("USER_TEXT", textToParse);
		String jsonSchema = "dmn_extractor";

		if (useCache) {
			JSONParser parser = new JSONParser();
			return (JSONObject) parser
					.parse(Files.readString(Path.of("cached/" + fileName + "_" + userPrompt + ".json")));
		} else {
			JSONObject result = llmClient.callLlmJson(
					List.of(new LlmMessage("system", ResourcesHandler.getPromptByName(systemPrompt)),
							new LlmMessage("user", ResourcesHandler.getPromptByName(userPrompt, promptArgs))),
					ResourcesHandler.getJsonSchemaByName(jsonSchema), temperature);
			File cacheDir = new File("cached");
			if (!cacheDir.exists()) {
				cacheDir.mkdir();
			}
			try (FileWriter file = new FileWriter(cacheDir.getPath() + "/" + fileName + "_" + userPrompt + ".json")) {
				file.write(result.toJSONString());
			}
			return result;
		}
	}
}
