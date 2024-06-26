package com._4paradigm.openmldb.featureplatform.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Repository
public class DagToolService {

    private Map<String, String> taskSql = new HashMap<>();
    private Map<String, List<String>> dependency = new HashMap<>();
    private List<String> convertedSql = new ArrayList<>();
    private List<Pair<String, String>> edgeList = new ArrayList<>();

    private class Pair<F, S> {
        private final F first;
        private final S second;

        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }

        public F getFirst() {
            return first;
        }

        public S getSecond() {
            return second;
        }
    }


    public String convertDagtoSQL(String dagJSON) throws SQLException {

        cleanup();
        readDAG(dagJSON);
	String newSQLCode = "";

        if (taskSql.size() == 0){
		return newSQLCode;
	}

        newSQLCode = convertSQL();
        return newSQLCode;

    }

    public String convertSQLtoDag(String sqlCode) throws SQLException {

    	String newDAGJson = "Placeholder "+ sqlCode;
    	return newDAGJson;
    }


    private void cleanup() {
        taskSql.clear();
        dependency.clear();
        convertedSql.clear();
        edgeList.clear();
    }

    private void readDAG(String dagData) {
        Gson gson = new Gson();

        List<JsonObject> dagItemList = gson.fromJson(dagData, new TypeToken<List<JsonObject>>() {}.getType());

        for (JsonObject dagItem : dagItemList) {
            if (dagItem.has("source")) { //Edge item
                String sourceCell = dagItem.getAsJsonObject("source").get("cell").getAsString();
                String targetCell = dagItem.getAsJsonObject("target").get("cell").getAsString();
                edgeList.add(new Pair<>(sourceCell, targetCell));
            } else { //Node item
                String taskId = dagItem.get("id").getAsString();
                String sqlCode = dagItem.getAsJsonObject("data").get("desc").getAsString();
                taskSql.put(taskId, sqlCode);
            }

        }

        for (String key : taskSql.keySet()) {
            dependency.put(key, new ArrayList<>());
        }

        for (Pair<String, String> edge : edgeList) {
            String source = edge.getFirst();
            String target = edge.getSecond();
            dependency.get(target).add(source);
        }


    }


    private String convertSQL() {
        List<String> convertedTasks = new ArrayList<>();

        for (String taskId : taskSql.keySet()) {
            convertTask(taskId, convertedTasks);
        }

        return taskSql.get(convertedTasks.get(convertedTasks.size() - 1));
    }

    private void convertTask(String taskId, List<String> convertedTasks) {
        if (!convertedTasks.contains(taskId)) {
            List<String> dependenciesList = dependency.get(taskId);
            for (String dependencyItem : dependenciesList) {
                convertTask(dependencyItem, convertedTasks);
            }
            String sqlCode = taskSql.get(taskId);
            modSQL(taskId, dependenciesList);
            convertedTasks.add(taskId);
        }
    }


    private void modSQL(String taskId, List<String> dependenciesList) {
        if (!dependenciesList.isEmpty()) {
            int counter = 0;
            StringBuilder finalSql = new StringBuilder("WITH");

            for (String dependencyItem : dependenciesList) {
                finalSql.append(" in").append(counter).append(" as (");
                finalSql.append(taskSql.get(dependencyItem));
                finalSql.append("),");
                counter++;
            }

            finalSql.deleteCharAt(finalSql.length() - 1); // Remove the trailing comma
            finalSql.append(" ").append(taskSql.get(taskId));
            taskSql.put(taskId, finalSql.toString());
        }
    }


}
