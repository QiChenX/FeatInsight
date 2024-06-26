package com._4paradigm.openmldb.featureplatform.service;

import com._4paradigm.openmldb.common.Pair;
import com._4paradigm.openmldb.featureplatform.dao.model.FeatureService;
import com._4paradigm.openmldb.featureplatform.dao.model.FeatureView;
import com._4paradigm.openmldb.featureplatform.dao.model.SimpleTableInfo;
import com._4paradigm.openmldb.featureplatform.dao.model.ThreadLocalSqlExecutor;
import com._4paradigm.openmldb.featureplatform.utils.OpenmldbSqlUtil;
import com._4paradigm.openmldb.featureplatform.utils.OpenmldbTableUtil;
import com._4paradigm.openmldb.proto.Common;
import com._4paradigm.openmldb.proto.NS;
import com._4paradigm.openmldb.sdk.Schema;
import com._4paradigm.openmldb.sdk.impl.SqlClusterExecutor;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Repository
public class TableService {

    public List<SimpleTableInfo> getTables() throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        ArrayList<SimpleTableInfo> simpleTableInfos = new ArrayList<>();

        List<String> databases = sqlExecutor.showDatabases();
        for (String database : databases) {
            if (!database.equals("SYSTEM_FEATURE_PLATFORM")) { // Ignore the system tables
                List<String> tables = sqlExecutor.getTableNames(database);
                for (String table : tables) {
                    Schema schema = sqlExecutor.getTableSchema(database, table);
                    String schemaString = schema.toString();
                    SimpleTableInfo simpleTableInfo = new SimpleTableInfo(database, table, schemaString);
                    simpleTableInfos.add(simpleTableInfo);
                }
            }
        }

        return simpleTableInfos;
    }

    public SimpleTableInfo getTable(String db, String table) throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        Schema schema = sqlExecutor.getTableSchema(db, table);
        String schemaString = schema.toString();
        SimpleTableInfo simpleTableInfo = new SimpleTableInfo(db, table, schemaString);
        return simpleTableInfo;
    }

    public String getTableSchema(String db, String table) throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        Schema schema = sqlExecutor.getTableSchema(db, table);
        return schema.toString();
    }

    public List<FeatureService> getRelatedFeatureServices(String db, String table) throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        List<FeatureService> relatedFeatureServices = new ArrayList<>();

        // Get all feature services
        FeatureServiceService featureServiceService = new FeatureServiceService();
        List<FeatureService> allFeatureServices = featureServiceService.getFeatureServices();

        for (FeatureService featureService : allFeatureServices) {

            String selectSql = OpenmldbSqlUtil.removeDeployFromSql(featureService.getSql());

            List<Pair<String, String>> dependentTables = SqlClusterExecutor.getDependentTables(selectSql,
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
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        List<FeatureView> relatedFeatureViews = new ArrayList<>();

        // Get all feature services
        FeatureViewService featureViewService = new FeatureViewService();
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

    public void deleteTable(String db, String table) throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        Statement statement = sqlExecutor.getStatement();
        statement.execute("SET @@execute_mode='online'");

        String sql = String.format("DROP TABLE %s.%s", db, table);
        statement.execute(sql);

        statement.close();
    }

    public List<String> getIndexNames(String db, String table) throws SQLException {
        SqlClusterExecutor sqlExecutor = ThreadLocalSqlExecutor.getSqlExecutor();
        NS.TableInfo tableInfo = sqlExecutor.getTableInfo(db, table);

        // For example: ["name", "name,age"]
        List<String> indexColumnNames = new ArrayList<>();

        for (Common.ColumnKey columnKey: tableInfo.getColumnKeyList()) {
            if(columnKey.getFlag() == 0){
                List<String> columnNameList = new ArrayList<>();
                for (String columnName: columnKey.getColNameList()) {
                    columnNameList.add(columnName);
                }

                indexColumnNames.add(String.join(",", columnNameList));
            }
        }

        return indexColumnNames;
    }

}