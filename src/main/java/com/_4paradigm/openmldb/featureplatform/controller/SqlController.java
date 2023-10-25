package com._4paradigm.openmldb.featureplatform.controller;

import com._4paradigm.openmldb.featureplatform.service.SqlService;
import com._4paradigm.openmldb.featureplatform.dao.model.SqlRequest;
import com._4paradigm.openmldb.featureplatform.utils.ResultSetUtil;
import com._4paradigm.openmldb.jdbc.SQLResultSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/sql")
public class SqlController {

    private final SqlService sqlService;

    @Autowired
    public SqlController(SqlService sqlService) {
        this.sqlService = sqlService;
    }

    @PostMapping("/execute")
    public ResponseEntity<String> executeSqlr(@RequestBody SqlRequest sqlRequest) {
        try {
            SQLResultSet resultSet = sqlService.executeSql(sqlRequest.getSql(), sqlRequest.isOnline());
            String responseMessage = "Success to execute sql: " + sqlRequest.getSql();
            if (resultSet != null) {
                responseMessage = ResultSetUtil.resultSetToString(resultSet);
                resultSet.close();
                System.out.println("Result set to string: " + responseMessage);
            }
            return new ResponseEntity<>(responseMessage, HttpStatus.OK);
        } catch (SQLException e) {
            return new ResponseEntity<>("Fail to execute sql and get exception: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<String> validateSql(@RequestBody SqlRequest sqlRequest) throws SQLException {
        List<String> result = sqlService.validateSql(sqlRequest.getSql());
        if (result.size() == 0) {
            return new ResponseEntity<>("Success to validate sql", HttpStatus.OK);
        } else {
            return new ResponseEntity<>(String.join(",", result), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/import")
    public ResponseEntity<String> importSql(@RequestBody SqlRequest sqlRequest) {
        try {
            SQLResultSet resultSet = sqlService.executeSql(sqlRequest.getSql(), sqlRequest.isOnline());
            String responseMessage = "Success to execute sql: " + sqlRequest.getSql();
            if (resultSet != null) {
                responseMessage = ResultSetUtil.resultSetToString(resultSet);
                resultSet.close();
                System.out.println("Result set to string: " + responseMessage);
            }
            return new ResponseEntity<>(responseMessage, HttpStatus.OK);
        } catch (SQLException e) {
            return new ResponseEntity<>("Fail to execute sql and get exception: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}