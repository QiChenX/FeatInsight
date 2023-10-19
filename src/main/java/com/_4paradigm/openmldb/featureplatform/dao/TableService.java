package com._4paradigm.openmldb.featureplatform.dao;

import com._4paradigm.openmldb.common.Pair;
import com._4paradigm.openmldb.featureplatform.dao.model.FeatureService;
import com._4paradigm.openmldb.featureplatform.dao.model.FeatureView;
import com._4paradigm.openmldb.featureplatform.dao.model.SimpleTableInfo;
import com._4paradigm.openmldb.featureplatform.utils.OpenmldbSdkUtil;
import com._4paradigm.openmldb.featureplatform.utils.OpenmldbTableUtil;
import com._4paradigm.openmldb.sdk.Schema;
import com._4paradigm.openmldb.sdk.impl.SqlClusterExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Repository
public class TableService {

    private final Environment env;

    @Autowired
    public TableService(Environment env) {
        this.env = env;
    }

    public List<SimpleTableInfo> getTables() throws SQLException {
        SqlClusterExecutor sqlExecutor = OpenmldbSdkUtil.getSqlExecutor(env);

        ArrayList<SimpleTableInfo> simpleTableInfos = new ArrayList<>();

        List<String> databases = sqlExecutor.showDatabases();
        for (String database : databases) {
            List<String> tables = sqlExecutor.getTableNames(database);
            for (String table : tables) {
                Schema schema = sqlExecutor.getTableSchema(database, table);
                String schemaString = schema.toString();
                SimpleTableInfo simpleTableInfo = new SimpleTableInfo(database, table, schemaString);
                simpleTableInfos.add(simpleTableInfo);
            }
        }

        return simpleTableInfos;
    }

    public SimpleTableInfo getTable(String db, String table) throws SQLException {
        SqlClusterExecutor sqlExecutor = OpenmldbSdkUtil.getSqlExecutor(env);

        Schema schema = sqlExecutor.getTableSchema(db, table);
        String schemaString = schema.toString();
        SimpleTableInfo simpleTableInfo = new SimpleTableInfo(db, table, schemaString);
        return simpleTableInfo;
    }

    public List<FeatureService> getRelatedFeatureServices(String db, String table) throws SQLException {
        SqlClusterExecutor sqlExecutor = OpenmldbSdkUtil.getSqlExecutor(env);

        List<FeatureService> relatedFeatureServices = new ArrayList<>();

        // Get all feature services
        FeatureServiceService featureServiceService = new FeatureServiceService(env);
        List<FeatureService> allFeatureServices = featureServiceService.getFeatureServices();

        for (FeatureService featureService : allFeatureServices) {
            List<Pair<String, String>> dependentTables = SqlClusterExecutor.getDependentTables(featureService.getSql(),
                    featureService.getDb(), OpenmldbTableUtil.getSystemSchemaMaps(sqlExecutor));

            for (Pair<String, String> tableItem : dependentTables) {
                String currentDb = tableItem.getKey();
                String currentTable = tableItem.getValue();

                if (db.equals(currentDb) && table.equals(currentTable)) {
                    // Add to result if equal
                    relatedFeatureServices.add(featureService);
                }
            }
        }

        return relatedFeatureServices;
    }


    public List<FeatureView> getRelatedFeatureViews(String db, String table) throws SQLException {
        SqlClusterExecutor sqlExecutor = OpenmldbSdkUtil.getSqlExecutor(env);

        List<FeatureView> relatedFeatureViews = new ArrayList<>();

        // Get all feature services
        FeatureViewService featureViewService = new FeatureViewService(this.env);
        List<FeatureView> allFeatureViews = featureViewService.getFeatureViews();

        for (FeatureView featureView : allFeatureViews) {
            List<Pair<String, String>> dependentTables = SqlClusterExecutor.getDependentTables(featureView.getSql(),
                    featureView.getDb(), OpenmldbTableUtil.getSystemSchemaMaps(sqlExecutor));

            for (Pair<String, String> tableItem : dependentTables) {
                String currentDb = tableItem.getKey();
                String currentTable = tableItem.getValue();

                if (db.equals(currentDb) && table.equals(currentTable)) {
                    // Add to result if equal
                    relatedFeatureViews.add(featureView);
                }
            }
        }

        return relatedFeatureViews;
    }

}