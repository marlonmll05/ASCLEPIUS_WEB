package com.certificadosapi.certificados.controller;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.certificadosapi.certificados.config.DatabaseConfig;

@RestController
@RequestMapping("/citas")
public class CitasController {

    private final DatabaseConfig databaseConfig;

    @Autowired
    public CitasController(DatabaseConfig databaseConfig){
        this.databaseConfig = databaseConfig;
    }


    @GetMapping("/agenda-tablas")
    public ResponseEntity<?> getAgendaTablas(@RequestParam Integer idTabla, @RequestParam(required = false) String Id) {
        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoft100_ST");

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {
                String sql = "EXEC dbo.pa_Agenda_Tablas ?, ?";

                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, idTabla);
                    pstmt.setString(2, Id);

                    try (ResultSet rs = pstmt.executeQuery()) {
                        List<Map<String, Object>> resultados = new ArrayList<>();
                        ResultSetMetaData meta = rs.getMetaData();
                        int colCount = meta.getColumnCount();

                        while (rs.next()) {
                            Map<String, Object> fila = new LinkedHashMap<>();
                            for (int i = 1; i <= colCount; i++) {
                                String colName = meta.getColumnName(i);
                                Object value = rs.getObject(i);
                                fila.put(colName, (value instanceof String) ? value.toString().trim() : value);
                            }
                            resultados.add(fila);
                        }

                        return ResponseEntity.ok(resultados);
                    }
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al ejecutar pa_Agenda_Tablas: " + e.getMessage());
        }
    }
        
    
}
