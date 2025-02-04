package edu.upc.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.camunda.bpm.model.dmn.*;
import org.camunda.bpm.model.dmn.instance.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class DmnGenerationHandler {
	public static DmnModelInstance generateDmnFromLlmJson(JSONObject llmOutput) {
		// Convert the JSON produced by the LLM to a DMN model
		// https://docs.camunda.org/manual/7.22/user-guide/model-api/dmn-model-api/create-a-model/
		// https://docs.camunda.org/javadoc/camunda-bpm-platform/7.6/index.html?org/camunda/bpm/model/dmn/Dmn.html
		DmnModelInstance modelInstance = Dmn.createEmptyModel();
		Definitions definitions = modelInstance.newInstance(Definitions.class);
		definitions.setNamespace("http://camunda.org/schema/1.0/dmn");
		definitions.setName("definitions");
		definitions.setId("definitions");
		modelInstance.setDefinitions(definitions);

		HashMap<String, Object> requirementsDiagramNodes = new HashMap<>();
		HashMap<String, Integer> idCounters = new HashMap<>();

		// Decisions
		JSONArray decisions = (JSONArray) ((JSONObject) llmOutput.get("requirementsDiagram")).get("decisionNodes");
		for (Object d : decisions) {
			JSONObject decision = (JSONObject) d;
			Decision dmnD = modelInstance.newInstance(Decision.class);
			String name = (String) decision.get("name");
			String id = nameToId(name);
			dmnD.setId(id);
			dmnD.setName(name);
			definitions.addChildElement(dmnD);

			requirementsDiagramNodes.put(id, dmnD);
		}

		JSONArray inputDatas = (JSONArray) ((JSONObject) llmOutput.get("requirementsDiagram")).get("inputNodes");
		for (Object d : inputDatas) {
			JSONObject input = (JSONObject) d;
			InputData dmnInput = modelInstance.newInstance(InputData.class);
			String name = (String) input.get("name");
			String id = nameToId(name);
			dmnInput.setId(id);
			dmnInput.setName(name);
			definitions.getDomElement().appendChild(dmnInput.getDomElement());

			requirementsDiagramNodes.put(id, dmnInput);
		}

		// Connections
		JSONArray decisionNodes = (JSONArray) ((JSONObject) llmOutput.get("requirementsDiagram")).get("decisionNodes");
		for (Object d : decisionNodes) {
			JSONObject decision = (JSONObject) d;
			String decisionId = nameToId((String) decision.get("name"));
			Decision dmnDecision = (Decision) requirementsDiagramNodes.get(decisionId);

			JSONArray inputs = (JSONArray) decision.get("inputs");
			for (Object input : inputs) {
				String inputId = nameToId((String) input);
				Object sourceNode = requirementsDiagramNodes.get(inputId);

				InformationRequirement requirement = modelInstance.newInstance(InformationRequirement.class);
				if (sourceNode instanceof InputData) {
					requirement.setRequiredInput((InputData) sourceNode);
				} else if (sourceNode instanceof Decision) {
					requirement.setRequiredDecision((Decision) sourceNode);
				}
				dmnDecision.addChildElement(requirement);
			}
		}

		// Decision Tables
		Integer ruleId = 0;
		JSONArray decisionTables = (JSONArray) llmOutput.get("decisionTables");
		for (Object dt : decisionTables) {
			JSONObject decisionTable = (JSONObject) dt;
			String decisionId = nameToId((String) decisionTable.get("decisionNodeName"));
			Decision dmnDecision = (Decision) requirementsDiagramNodes.get(decisionId);

			DecisionTable dmnTable = modelInstance.newInstance(DecisionTable.class);
			dmnTable.setId("DecisionTable_" + decisionId);
			dmnDecision.addChildElement(dmnTable);

			HashMap<String, Input> inputs = new HashMap<>();
			List<Input> inputList = new ArrayList<>();

			// Add Inputs to DecisionTable
			JSONArray rules = (JSONArray) decisionTable.get("rules");
			for (Object r : rules) {
				JSONObject rule = (JSONObject) r;
				JSONArray conditions = (JSONArray) rule.get("conditions");
				for (Object c : conditions) {
					JSONObject condition = (JSONObject) c;
					String inputKey = nameToId(condition.get("data").toString());
					if (!inputs.containsKey(inputKey)) {
						Input input = modelInstance.newInstance(Input.class);
						String baseInputId = "InputClause_" + inputKey;
						input.setId(getUniqueId(baseInputId, idCounters));
						input.setLabel(condition.get("data").toString());
						dmnTable.addChildElement(input);

						InputExpression inputExpression = modelInstance.newInstance(InputExpression.class);
						String baseExpressionId = "LiteralExpression_" + inputKey;
						inputExpression.setId(getUniqueId(baseExpressionId, idCounters));
						Text inputText = modelInstance.newInstance(Text.class);
						inputText.setTextContent(condition.get("data").toString());
						inputExpression.setText(inputText);
						input.setInputExpression(inputExpression);
						inputs.put(inputKey, input);
						inputList.add(input);
					}
				}
			}

			// Add Output to DecisionTable
			Output output = modelInstance.newInstance(Output.class);
			output.setId("OutputClause_" + decisionId);
			dmnTable.addChildElement(output);

			// Add Rules to DecisionTable
			for (Object r : rules) {
				JSONObject rule = (JSONObject) r;
				Rule dmnRule = modelInstance.newInstance(Rule.class);
				dmnRule.setId("rule-" + (++ruleId));

				int inputIndex = 0;
				for (Input input : inputList) {
					InputEntry inputEntry = modelInstance.newInstance(InputEntry.class);
					inputEntry.setId("inputEntry_" + ruleId + "_" + inputIndex);
					Text text = modelInstance.newInstance(Text.class);
					text.setTextContent(getFeelExpression(rule, input.getLabel()));
					inputEntry.setText(text);
					dmnRule.addChildElement(inputEntry);
					inputIndex++;
				}

				OutputEntry outputEntry = modelInstance.newInstance(OutputEntry.class);
				outputEntry.setId("outentry_" + ruleId);
				Text outputText = modelInstance.newInstance(Text.class);
				outputText.setTextContent("\"" + rule.get("decision") + "\"");
				outputEntry.setText(outputText);
				dmnRule.addChildElement(outputEntry);

				dmnTable.addChildElement(dmnRule);
			}
		}

		return modelInstance;
	}

	// Convert name into a valid DMN NCName
	public static String nameToId(String name) {
		name = name.replaceAll("[^a-zA-Z0-9]", "_");
		if (Character.isDigit(name.charAt(0))) {
			name = "_" + name;
		}
		return name;
	}

	// Generate a FEEL expression from a rule
	private static String getFeelExpression(JSONObject rule, String inputLabel) {
		JSONArray conditions = (JSONArray) rule.get("conditions");
		for (Object c : conditions) {
			JSONObject condition = (JSONObject) c;
			if (condition.get("data").toString().equals(inputLabel)) {
				String operator = condition.get("operator").toString();
				if (operator.equals("eq")) {
					return "'" + condition.get("value") + "'";
				} else if (operator.equals("inRange")) {
					boolean hasFrom = condition.containsKey("from") && condition.get("from") != null;
					boolean hasTo = condition.containsKey("to") && condition.get("to") != null;

					String from = hasFrom ? condition.get("from").toString() : "";
					String to = hasTo ? condition.get("to").toString() : "";
					if (!from.isEmpty() && !to.isEmpty()) {
						return "[" + from + ".." + to + "]";
					} else if (!from.isEmpty()) {
						return ">" + from;
					} else if (!to.isEmpty()) {
						return "<" + to;
					}
				}
			}
		}
		return "";
	}

	// Helper to append counter
	private static String getUniqueId(String base, HashMap<String, Integer> counters) {
		counters.putIfAbsent(base, 0);
		counters.put(base, counters.get(base) + 1);
		int count = counters.get(base);
		return (count > 1) ? base + "_" + count : base;
	}
}
