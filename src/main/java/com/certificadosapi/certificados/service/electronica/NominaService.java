package com.certificadosapi.certificados.service.electronica;

import com.certificadosapi.certificados.config.DatabaseConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;

public class NominaService {

    private static final Logger log = LoggerFactory.getLogger(NominaService.class);

    private final DatabaseConfig databaseConfig;

    @Autowired
    public NominaService(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    // -------------------------------------------------------------------------
    // OBTENER DATOS BASE (URL y Token)
    // -------------------------------------------------------------------------

    /**
     * Obtiene la URL del PAC de nómina y el Bearer Token desde ParametrosServidor.
     *
     * @param IdNomina      Identificador de la nómina.
     * @param IdContratoKey Identificador del contrato.
     * @return Mapa con "urlLatam" y "bearerToken".
     */
    public Map<String, String> obtenerDatosConexion(Integer IdNomina, Integer IdContratoKey) throws SQLException {

        log.info("[NominaService] Obteniendo URL y BearerToken para IdNomina={} IdContratoKey={}", IdNomina, IdContratoKey);

        String urlLatam;
        String bearerToken;

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"))) {

            String sqlUrl = "SELECT ValorParametro FROM ParametrosServidor WHERE NomParametro = 'NE_ExtApi_URL'";
            try (PreparedStatement ps = conn.prepareStatement(sqlUrl); ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("No se encontró NE_ExtApi_URL en ParametrosServidor");
                urlLatam = rs.getString("ValorParametro");
                log.debug("[NominaService] URL PAC obtenida: {}", urlLatam);
            }

            String sqlToken = "SELECT ValorParametro FROM ParametrosServidor WHERE NomParametro = 'FE_BearerToken'";
            try (PreparedStatement ps = conn.prepareStatement(sqlToken); ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("No se encontró FE_BearerToken en ParametrosServidor");
                bearerToken = rs.getString("ValorParametro");
                log.debug("[NominaService] BearerToken obtenido correctamente");
            }
        }

        return Map.of("urlLatam", urlLatam, "bearerToken", bearerToken);
    }

    // -------------------------------------------------------------------------
    // CONSTRUIR JSON DE NÓMINA
    // -------------------------------------------------------------------------

    /**
     * Construye el JSON de nómina electrónica consultando los SPs de la base de datos.
     *
     * @param IdEmpresaKey  Identificador de la empresa.
     * @param IdNomina      Identificador de la nómina.
     * @param IdContratoKey Identificador del contrato.
     * @return ObjectNode con el JSON completo listo para enviar al PAC.
     */
    public ObjectNode construirJsonNomina(Integer IdEmpresaKey, Integer IdNomina, Integer IdContratoKey) throws SQLException {

        log.info("[NominaService] Construyendo JSON de nómina para IdEmpresaKey={} IdNomina={} IdContratoKey={}",
                IdEmpresaKey, IdNomina, IdContratoKey);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"))) {

            // --- Info empresa ---
            try (CallableStatement stmt = conn.prepareCall("{call pa_NE_InfoEmpresa(?)}")) {
                stmt.setInt(1, IdEmpresaKey);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("pa_NE_InfoEmpresa no retornó datos para IdEmpresaKey=" + IdEmpresaKey);

                    log.debug("[NominaService] Datos de empresa obtenidos correctamente");
                    root.put("type_document_id", 9);
                    root.put("establishment_name", rs.getString("establishment_name"));
                    root.put("establishment_address", rs.getString("establishment_address"));
                    root.put("establishment_phone", rs.getString("establishment_phone"));
                    root.put("establishment_municipality", rs.getInt("establishment_municipality"));
                    root.put("establishment_email", rs.getString("establishment_email"));
                    root.put("head_note", "");
                    root.put("foot_note", "");

                    ObjectNode novelty = mapper.createObjectNode();
                    novelty.put("novelty", false);
                    novelty.put("uuidnov", "");
                    root.set("novelty", novelty);
                }
            }

            // --- Info básica nómina ---
            try (CallableStatement nominaStmt = conn.prepareCall("{call pa_NE_InfoBasicaNomina(?, ?)}")) {
                nominaStmt.setInt(1, IdNomina);
                nominaStmt.setInt(2, IdContratoKey);

                try (ResultSet rsNomina = nominaStmt.executeQuery()) {
                    boolean hayDatos = false;

                    while (rsNomina.next()) {
                        hayDatos = true;

                        ObjectNode period = mapper.createObjectNode();
                        period.put("admision_date", rsNomina.getString("admision_date"));
                        period.put("settlement_start_date", rsNomina.getString("settlement_start_date"));
                        period.put("settlement_end_date", rsNomina.getString("settlement_end_date"));
                        period.put("worked_time", rsNomina.getString("worked_time"));
                        period.put("issue_date", rsNomina.getString("issue_date"));
                        root.set("period", period);

                        root.put("sendmail", false);
                        root.put("sendmailtome", false);
                        root.put("worker_code", rsNomina.getString("worker_code"));
                        root.put("prefix", rsNomina.getString("prefix"));
                        root.put("consecutive", rsNomina.getInt("consecutive"));
                        root.put("payroll_period_id", rsNomina.getInt("payroll_period_id"));
                        root.set("notes", null);

                        ObjectNode worker = mapper.createObjectNode();
                        worker.put("type_worker_id", rsNomina.getInt("type_worker_id"));
                        worker.put("sub_type_worker_id", 1);
                        worker.put("payroll_type_document_identification_id", rsNomina.getInt("payroll_type_document_identification_id"));
                        worker.put("municipality_id", rsNomina.getInt("municipality_id"));
                        worker.put("type_contract_id", rsNomina.getInt("type_contract_id"));
                        worker.put("high_risk_pension", rsNomina.getInt("high_risk_pension") == 1);
                        worker.put("identification_number", rsNomina.getString("identification_number"));
                        worker.put("surname", rsNomina.getString("surname"));
                        worker.put("second_surname", rsNomina.getString("second_surname"));
                        worker.put("first_name", rsNomina.getString("first_name"));
                        worker.put("middle_name", rsNomina.getString("second_name"));
                        worker.put("address", rsNomina.getString("address"));
                        worker.put("integral_salarary", rsNomina.getInt("integral_salarary") == 1);
                        worker.put("salary", rsNomina.getString("salary"));
                        worker.put("email", rsNomina.getString("email"));
                        root.set("worker", worker);

                        ObjectNode payment = mapper.createObjectNode();
                        payment.put("payment_method_id", rsNomina.getInt("payment_method_id"));
                        payment.put("bank_name", rsNomina.getString("bank_name"));
                        payment.put("account_type", rsNomina.getString("account_type"));
                        payment.put("account_number", rsNomina.getString("account_number"));
                        root.set("payment", payment);

                        ArrayNode payment_dates = mapper.createArrayNode();
                        ObjectNode payment_date = mapper.createObjectNode();
                        payment_date.put("payment_date", rsNomina.getString("payment_date"));
                        payment_dates.add(payment_date);
                        root.set("payment_dates", payment_dates);

                        ObjectNode accrued = mapper.createObjectNode();
                        accrued.put("worked_days", rsNomina.getInt("worked_days"));
                        accrued.put("salary", rsNomina.getString("salary"));
                        accrued.put("accrued_total", rsNomina.getString("accrued_total"));
                        root.set("accrued", accrued);

                        log.debug("[NominaService] Info básica nómina cargada para worker={}", rsNomina.getString("identification_number"));
                    }

                    if (!hayDatos) throw new IllegalStateException("pa_NE_InfoBasicaNomina no retornó datos");
                }
            }

            // --- Deducciones ---
            root = construirDeducciones(conn, mapper, root, IdNomina, IdContratoKey);
        }

        log.info("[NominaService] JSON de nómina construido exitosamente");
        return root;
    }

    // -------------------------------------------------------------------------
    // CONSTRUIR DEDUCCIONES
    // -------------------------------------------------------------------------

    /**
     * Consulta y agrega el bloque de deducciones (salud, pensión y otras) al JSON raíz.
     */
    private ObjectNode construirDeducciones(Connection conn, ObjectMapper mapper, ObjectNode root,
                                             Integer IdNomina, Integer IdContratoKey) throws SQLException {

        log.debug("[NominaService] Construyendo deducciones para IdNomina={} IdContratoKey={}", IdNomina, IdContratoKey);

        try (CallableStatement pensionStmt = conn.prepareCall("{call PA_NE_Nomina_Deducciones_Pension(?, ?)}");
             CallableStatement saludStmt   = conn.prepareCall("{call PA_NE_Nomina_Deducciones_Salud(?, ?)}");
             CallableStatement otrasStmt   = conn.prepareCall("{call PA_NE_Nomina_Deducciones(?, ?)}")) {

            pensionStmt.setInt(1, IdNomina); pensionStmt.setInt(2, IdContratoKey);
            saludStmt.setInt(1, IdNomina);   saludStmt.setInt(2, IdContratoKey);
            otrasStmt.setInt(1, IdNomina);   otrasStmt.setInt(2, IdContratoKey);

            ObjectNode deductions = mapper.createObjectNode();
            boolean hayDatos = false;
            int epsDeduction = 0;
            int pensionDeduction = 0;

            // Salud
            try (ResultSet rsSalud = saludStmt.executeQuery()) {
                if (rsSalud.next()) {
                    hayDatos = true;
                    deductions.put("eps_type_law_deductions_id", rsSalud.getInt("eps_type_law_deductions_id"));
                    String epsStr = rsSalud.getString("eps_deduction");
                    deductions.put("eps_deduction", epsStr);
                    try { epsDeduction = Integer.parseInt(epsStr.trim()); } catch (NumberFormatException e) { epsDeduction = 0; }
                    log.debug("[NominaService] Deducción salud: {}", epsStr);
                }
            }

            // Pensión
            try (ResultSet rsPension = pensionStmt.executeQuery()) {
                if (rsPension.next()) {
                    hayDatos = true;
                    deductions.put("pension_type_law_deductions_id", rsPension.getInt("pension_type_law_deductions_id"));
                    String pensionStr = rsPension.getString("pension_deduction");
                    deductions.put("pension_deduction", pensionStr);
                    try { pensionDeduction = Integer.parseInt(pensionStr.trim()); } catch (NumberFormatException e) { pensionDeduction = 0; }
                    log.debug("[NominaService] Deducción pensión: {}", pensionStr);
                }
            }

            // Otras deducciones
            ArrayNode other_deductions = mapper.createArrayNode();
            int otherDeductionsTotal = 0;

            try (ResultSet rsOtras = otrasStmt.executeQuery()) {
                while (rsOtras.next()) {
                    hayDatos = true;
                    String nombreCampo = rsOtras.getString("TipoDeduccion_DIANN");
                    String valorStr    = rsOtras.getString("ValorDeduccion");
                    int valor = 0;
                    try { valor = Integer.parseInt(valorStr.trim()); } catch (NumberFormatException e) { valor = 0; }

                    if (nombreCampo != null && !nombreCampo.trim().isEmpty() && !nombreCampo.equals("other_deductions")) {
                        deductions.put(nombreCampo.trim(), String.valueOf(valor));
                    }
                    if (nombreCampo != null && nombreCampo.trim().equals("other_deductions")) {
                        ObjectNode other_deduction = mapper.createObjectNode();
                        other_deduction.put("other_deduction", String.valueOf(valor));
                        other_deductions.add(other_deduction);
                        otherDeductionsTotal += valor;
                    }
                }
            }

            // Total deducciones
            int deductionsTotal = epsDeduction + pensionDeduction + otherDeductionsTotal;
            Iterator<String> fieldNames = deductions.fieldNames();
            while (fieldNames.hasNext()) {
                String field = fieldNames.next();
                if (!field.equals("eps_deduction") && !field.equals("pension_deduction")
                        && !field.endsWith("_id") && !field.equals("deductions_total")) {
                    try { deductionsTotal += Integer.parseInt(deductions.get(field).asText()); }
                    catch (NumberFormatException ignored) {}
                }
            }

            deductions.put("deductions_total", String.valueOf(deductionsTotal));
            root.set("deductions", deductions);
            if (other_deductions.size() > 0) root.set("other_deductions", other_deductions);

            log.debug("[NominaService] Total deducciones calculado: {}", deductionsTotal);

            if (!hayDatos) throw new IllegalStateException("No se encontraron datos en Deducciones");
        }

        return root;
    }

    // -------------------------------------------------------------------------
    // ENVIAR AL PAC
    // -------------------------------------------------------------------------

    /**
     * Envía el JSON de nómina al PAC y procesa la respuesta.
     * Si el documento ya fue procesado anteriormente, consulta el estado por CUNE.
     *
     * @param IdNomina      Identificador de la nómina.
     * @param IdContratoKey Identificador del contrato.
     * @param jsonString    JSON serializado listo para enviar.
     * @param bearerToken   Token de autenticación.
     * @param urlEnvio      URL del endpoint del PAC.
     * @return Mapa con "statusCode", "cune", y opcionalmente "respuestaConsulta".
     */
    @SuppressWarnings("null")
    public Map<String, Object> enviarAlPac(Integer IdNomina, Integer IdContratoKey,
                                            String jsonString, String bearerToken, String urlEnvio) throws Exception {

        log.info("[NominaService] Enviando nómina al PAC. IdNomina={} IdContratoKey={} URL={}", IdNomina, IdContratoKey, urlEnvio);
        log.debug("[NominaService] BearerToken: {}", bearerToken);
        log.debug("[NominaService] JSON a enviar: {}", jsonString);

        ObjectMapper mapper = new ObjectMapper();
        RestTemplate template = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearerToken);
        headers.set("User-Agent", "PostmanRuntime/7.44.1");
        headers.set("Accept", "application/json");
        headers.set("Connection", "keep-alive");
        headers.set("Accept-Encoding", "gzip, deflate, br");

        HttpEntity<String> entity = new HttpEntity<>(jsonString, headers);

        ResponseEntity<String> respuesta = template.postForEntity(urlEnvio, entity, String.class);

        if (respuesta.getStatusCode() != HttpStatus.OK) {
            log.warn("[NominaService] Respuesta no exitosa del PAC: {}", respuesta.getBody());
            return Map.of("statusCode", respuesta.getStatusCode().toString(), "body", respuesta.getBody());
        }

        JsonNode jsonBody = mapper.readTree(respuesta.getBody());

        String errorMessage = jsonBody.path("ResponseDian").path("Envelope").path("Body")
                .path("SendNominaSyncResponse").path("SendNominaSyncResult").path("ErrorMessage").path("string").asText();

        String statusDescription = jsonBody.path("ResponseDian").path("Envelope").path("Body")
                .path("SendNominaSyncResponse").path("SendNominaSyncResult").path("StatusDescription").asText();

        String statusCode = jsonBody.path("ResponseDian").path("Envelope").path("Body")
                .path("SendNominaSyncResponse").path("SendNominaSyncResult").path("StatusCode").asText();

        String cune = jsonBody.has("cune") ? jsonBody.get("cune").asText() : "";
        String qr   = jsonBody.has("QRStr") ? jsonBody.get("QRStr").asText() : "";

        String payrollxml = jsonBody.has("payrollxml") ? jsonBody.get("payrollxml").asText() : "";
        byte[] xmlBytes   = Base64.getDecoder().decode(payrollxml);
        byte[] qrBytes    = qr.getBytes(StandardCharsets.UTF_8);

        log.info("[NominaService] Respuesta PAC - StatusCode={} CUNE={}", statusCode, cune);
        log.info("[NominaService] ErrorMessage DIAN: {}", errorMessage);

        String messageInsert = errorMessage.isEmpty() ? statusDescription : errorMessage;
        insertarMovimientoNomina(IdNomina, IdContratoKey, messageInsert, xmlBytes);

        if ("00".equals(statusCode)) {
            log.info("[NominaService] Nómina aceptada por la DIAN (StatusCode=00). Actualizando detalle...");
            actualizarNominaDetalle(xmlBytes, qrBytes, cune, IdContratoKey, IdNomina);
        }

        if (errorMessage.contains("Documento procesado anteriormente.")) {
            log.warn("[NominaService] Documento ya procesado anteriormente. Consultando por CUNE...");
            String respuestaConsulta = consultarPorCune(IdNomina, IdContratoKey, statusDescription, headers, template, mapper);
            return Map.of("statusCode", statusCode, "cune", cune, "respuestaConsulta", respuestaConsulta, "fueConsulta", true);
        }

        return Map.of("statusCode", statusCode, "cune", cune, "body", respuesta.getBody(), "fueConsulta", false);
    }

    // -------------------------------------------------------------------------
    // CONSULTAR POR CUNE (documento ya procesado)
    // -------------------------------------------------------------------------

    /**
     * Consulta el estado de un documento de nómina ya procesado usando el CUNE extraído de statusDescription.
     */
    private String consultarPorCune(Integer IdNomina, Integer IdContratoKey, String statusDescription,
                                     HttpHeaders headers, RestTemplate template, ObjectMapper mapper) throws Exception {

        String[] partes = statusDescription.split("CUNE");
        if (partes.length < 2) throw new IllegalStateException("No se pudo extraer el CUNE de statusDescription");

        String cune = partes[1].trim();
        log.info("[NominaService] CUNE extraído para consulta: {}", cune);

        ObjectNode consultaJson = mapper.createObjectNode();
        consultaJson.put("sendmail", true);
        consultaJson.put("sendmailtome", false);
        consultaJson.put("is_payroll", true);

        String jsonConsulta = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(consultaJson);
        log.debug("[NominaService] JSON consulta: {}", jsonConsulta);

        String urlConsulta = String.format("https://api.gihos-sas.com/api/ubl2.1/status/document/%s", cune);
        log.info("[NominaService] URL consulta: {}", urlConsulta);

        HttpEntity<String> consultaEntity = new HttpEntity<>(jsonConsulta, headers);

        @SuppressWarnings("null")
        ResponseEntity<String> respuestaConsulta = template.postForEntity(urlConsulta, consultaEntity, String.class);

        String consultaString = respuestaConsulta.getBody();
        if (consultaString == null || consultaString.isEmpty()) {
            throw new IllegalStateException("La consulta por CUNE no retornó contenido");
        }

        JsonNode consultaNode = mapper.readTree(consultaString);
        String payrollxml = consultaNode.path("ResponseDian").path("Envelope").path("Body")
                .path("GetStatusResponse").path("GetStatusResult").path("XmlBase64Bytes").asText();

        byte[] xmlBytes = Base64.getDecoder().decode(payrollxml);
        String qr       = String.format("https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=%s", cune);
        byte[] qrBytes  = qr.getBytes(StandardCharsets.UTF_8);

        log.info("[NominaService] QR consulta generado: {}", qr);

        actualizarNominaDetalle(xmlBytes, qrBytes, cune, IdContratoKey, IdNomina);

        return consultaString;
    }

    // -------------------------------------------------------------------------
    // PERSISTENCIA
    // -------------------------------------------------------------------------

    /**
     * Actualiza el detalle de nómina con el XML, QR y CUNE retornados por la DIAN.
     */
    public void actualizarNominaDetalle(byte[] xmlBytes, byte[] qrBytes, String cune,
                                         Integer IdContratoKey, Integer IdNomina) throws SQLException {

        log.info("[NominaService] Actualizando Nom_Nomina_Detalle para IdNomina={} IdContratoKey={} CUNE={}", IdNomina, IdContratoKey, cune);

        String sqlUpdate = """
            UPDATE Nom_Nomina_Detalle
            SET
                DocXmlGenerado    = ?,
                DocXmlEnviadoDian = ?,
                DocXmlEnvelope    = ?,
                CodigoQR          = ?,
                CUNE              = ?
            WHERE idContratoKey = ?
            AND   idNomina      = ?
        """;

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"));
             PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {

            ps.setInt(1, 1);
            ps.setInt(2, 3);
            ps.setBytes(3, xmlBytes);
            ps.setBytes(4, qrBytes);
            ps.setString(5, cune);
            ps.setInt(6, IdContratoKey);
            ps.setInt(7, IdNomina);

            int filas = ps.executeUpdate();
            log.info("[NominaService] UPDATE Nom_Nomina_Detalle ejecutado. Filas afectadas: {}", filas);
        }
    }

    /**
     * Inserta un registro de solicitud de nómina electrónica en Nom_Nomina_Detalle_NE_Solicitudes.
     */
    public void insertarMovimientoNomina(Integer IdNomina, Integer IdContratoKey,
                                          String errorMessage, byte[] xmlBytes) throws SQLException {

        log.info("[NominaService] Insertando movimiento de nómina. IdNomina={} IdContratoKey={}", IdNomina, IdContratoKey);

        Integer idNominaDet = null;

        String sqlGetId = """
            SELECT IdNominaDet FROM Nom_Nomina_Detalle
            WHERE idNomina = ? AND idContratoKey = ?
        """;

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"));
             PreparedStatement ps = conn.prepareStatement(sqlGetId)) {

            ps.setInt(1, IdNomina);
            ps.setInt(2, IdContratoKey);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    idNominaDet = rs.getInt("IdNominaDet");
                    log.debug("[NominaService] IdNominaDet encontrado: {}", idNominaDet);
                } else {
                    throw new SQLException("No se encontró IdNominaDet para IdNomina=" + IdNomina + " IdContratoKey=" + IdContratoKey);
                }
            }
        }

        String sqlInsert = """
            INSERT INTO Nom_Nomina_Detalle_NE_Solicitudes (
                IdNomina, IdContratoKey, IdNominaDet, IdTipoSolicitud,
                FechaSolicitud, EstadoSolicitud, EstadoValidacion, DescripcionEstado, RespXml
            ) VALUES (?,?,?,?,GETDATE(),?,?,?,?)
        """;

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"));
             PreparedStatement ps = conn.prepareStatement(sqlInsert)) {

            ps.setInt(1, IdNomina);
            ps.setInt(2, IdContratoKey);
            ps.setInt(3, idNominaDet);
            ps.setInt(4, 1);
            ps.setInt(5, 2);
            ps.setInt(6, 0);
            ps.setString(7, errorMessage);
            ps.setBytes(8, xmlBytes);

            ps.executeUpdate();
            log.info("[NominaService] INSERT Nom_Nomina_Detalle_NE_Solicitudes ejecutado correctamente");
        }
    }

}
