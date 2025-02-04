package edu.upc;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.json.simple.JSONObject;

import edu.upc.handler.DmnFromLlmHandler;
import edu.upc.handler.DmnGenerationHandler;
import edu.upc.llm.LlmClient;
import edu.upc.utils.ResourcesHandler;

import org.camunda.bpm.model.dmn.*;

public class DmnExtractorLlm {

	@SuppressWarnings("unused")
	public static void main(String[] args) throws Exception {
		String folder = getFolderPathFromResource("texts").toString();
		File[] filePaths = new File[1];
		String fileName = null;
		fileName = folder + "/" + "4_Dataset_1" + ".txt"; // This is the file that will be parsed. comment this line
															// to parse all files
		boolean useCache = false; // set to true to use cached data for parsing (faster) or false to fetch data
									// from LLM (slower)
		float temperature = 1f; // set the temperature for the LLM model (1 is the default for API ChatGPT)

		if (fileName == null)
			filePaths = new File(folder).listFiles();
		else
			filePaths[0] = new File(fileName);
		Arrays.sort(filePaths);
		for (File path : filePaths) {
			LlmClient llmClient = new LlmClient();
			fileName = getFileNameWithoutExtension(path);
			String textToParse = ResourcesHandler.getTextByName(fileName);
			System.out.println("------------------------------------------------------------------");
			System.out.println("Parsing file: " + fileName);
			System.out.println("------------------------------------------------------------------");
			DmnFromLlmHandler extractor = new DmnFromLlmHandler(llmClient, fileName);
			JSONObject llmOutput = extractor.extractDMN(textToParse, useCache, temperature);
			System.out.println("LLM produced JSON output:\n" + llmOutput.toJSONString());
			DmnModelInstance modelInstance = DmnGenerationHandler.generateDmnFromLlmJson(llmOutput);
			Dmn.validateModel(modelInstance);
			File file = new File("outputs/" + fileName + ".dmn");
			Dmn.writeModelToFile(file, modelInstance);
		}
		System.out.println("Done!");
	}

	private static Path getFolderPathFromResource(String folderName) {
		return Paths.get("src", "main", "resources", folderName).toAbsolutePath();
	}

	private static String getFileNameWithoutExtension(File file) {
		String fileName = "";
		try {
			if (file != null && file.exists()) {
				String name = file.getName();
				fileName = name.replaceFirst("[.][^.]+$", "");
			}
		} catch (Exception e) {
			e.printStackTrace();
			fileName = "";
		}
		return fileName;

	}

}
