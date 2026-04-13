package com.certificadosapi.certificados.service.electronica;

import org.springframework.web.client.RestTemplate;

import com.certificadosapi.certificados.config.DatabaseConfig;
import com.certificadosapi.certificados.dto.ArchivoDescarga;
import com.certificadosapi.certificados.util.ServidorUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;

@Service
public class FelectronicaService {

    private final DatabaseConfig databaseConfig;
    private final ServidorUtil servidorUtil;
    private static final Logger log = LoggerFactory.getLogger(FelectronicaService.class);

    @Autowired
    FelectronicaService(DatabaseConfig databaseConfig, ServidorUtil servidorUtil){
        this.databaseConfig = databaseConfig;
        this.servidorUtil = servidorUtil;
    }

    // -------------------------------------------------------------------------
    // GENERAR FACTURA ELECTRÓNICA
    // -------------------------------------------------------------------------

    /**
     * Obtiene la URL del PAC, el Bearer Token y el JSON de factura desde la base de datos.
     *
     * @param IdMovDoc Identificador del movimiento documento.
     * @return Mapa con las claves "urlPac", "bearerToken" y "json".
     * @throws SQLException          si ocurre un error en la consulta.
     * @throws IllegalStateException si no se encuentra alguno de los valores requeridos.
     */
    public Map<String, String> obtenerDatosFactura(Integer IdMovDoc) throws SQLException {

        String urlPac;

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"))) {
            String sql = "SELECT ValorParametro FROM ParametrosServidor WHERE NomParametro = 'FE_ExtApi_URL'";
            try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("No se encontró FE_ExtApi_URL en ParametrosServidor");
                urlPac = rs.getString("ValorParametro");
            }
        }

        String bearerToken;
        String json;
        String sqlToken = """
            SELECT ValorParametro FROM ParametrosServidor PS
            INNER JOIN DBO.Empresas E ON E.IdEmpresaGrupo = PS.IdEmpresaGrupo
            INNER JOIN DBO.MovimientoDocumentos MD ON MD.IdEmpresaKey = E.IdEmpresaKey
            WHERE PS.NomParametro = 'FE_BearerToken' AND MD.IdMovDoc = ?
        """;

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"))) {
            try (PreparedStatement ps = conn.prepareStatement(sqlToken)) {
                ps.setInt(1, IdMovDoc);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("No se encontró el BearerToken en ParametrosServidor");
                    bearerToken = rs.getString("ValorParametro");
                }
            }

            try (CallableStatement stmt = conn.prepareCall("{call pa_FE_GenerarJSONFacturaDIAN(?)}")) {
                stmt.setInt(1, IdMovDoc);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("No se encontraron datos en pa_FE_GenerarJSONFacturaDIAN");
                    json = rs.getString("JSONFactura");
                    if (json == null) throw new IllegalStateException("El JSON retornado es NULL");
                }
            }
        }

        return Map.of("urlPac", urlPac, "bearerToken", bearerToken, "json", json);
    }


    /**
     * Envía el JSON de factura al PAC y procesa la respuesta de la DIAN.
     * Si el documento ya fue enviado anteriormente, consulta el estado por CUFE.
     *
     * @param IdMovDoc    Identificador del movimiento documento.
     * @param json        JSON de factura generado por el SP.
     * @param bearerToken Token de autenticación para el PAC.
     * @param urlEnvio    URL del endpoint del PAC.
     * @return Mapa con los resultados: "statusCode", "cufe", y opcionalmente "consultaBody".
     * @throws RuntimeException si ocurre un error al enviar o procesar la respuesta.
     */
    public Map<String, Object> enviarAlPac(Integer IdMovDoc, String json, String bearerToken, String urlEnvio) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RestTemplate template = servidorUtil.crearRestTemplateInseguro();

        HttpHeaders headers = construirHeaders(bearerToken);
        HttpEntity<String> entity = new HttpEntity<>(json, headers);

        @SuppressWarnings("null")
        ResponseEntity<String> respuesta = template.postForEntity(urlEnvio, entity, String.class);

        if (respuesta.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Respuesta no exitosa del PAC: " + respuesta.getBody());
        }

        JsonNode jsonBody = mapper.readTree(respuesta.getBody());

        String message     = jsonBody.has("message") ? jsonBody.get("message").asText() : "";
        String statusCode  = jsonBody.path("ResponseDian").path("Envelope").path("Body")
                                .path("SendBillSyncResponse").path("SendBillSyncResult").path("StatusCode").asText();
        String cufe        = jsonBody.has("cufe") ? jsonBody.get("cufe").asText() : "";
        String qr          = jsonBody.has("QRStr") ? jsonBody.get("QRStr").asText() : "";

        byte[] xmlBytesExitoso = Base64.getDecoder().decode(jsonBody.path("attacheddocument").asText());
        byte[] byteQr          = qr.getBytes(StandardCharsets.UTF_8);
        byte[] xmlBytes        = Base64.getDecoder().decode(jsonBody.path("invoicexml").asText());

        String erroresDian = extraerErroresDian(jsonBody);
        log.info("Errores DIAN: {}", erroresDian);

        insertarMovimientoFactura(IdMovDoc, erroresDian, xmlBytes);

        if ("00".equals(statusCode)) {
            actualizarFacturaMovimientos(3, xmlBytesExitoso, byteQr, cufe, IdMovDoc);
        }

        if (message.contains("Este documento ya fue enviado anteriormente") || erroresDian.contains("Documento procesado anteriormente")) {
            String consultaBody = consultarPorCufe(IdMovDoc, cufe, headers, template, mapper);
            return Map.of("statusCode", statusCode, "cufe", cufe, "consultaBody", consultaBody, "fueConsulta", true);
        }

        return Map.of("statusCode", statusCode, "cufe", cufe, "fueConsulta", false);
    }

    /**
     * Consulta el estado de una factura ya procesada por CUFE y actualiza la base de datos.
     *
     * @param IdMovDoc Identificador del movimiento documento.
     * @param cufe     Código Único de Factura Electrónica.
     * @param headers  Headers HTTP ya configurados con el token.
     * @param template RestTemplate para ejecutar la petición.
     * @param mapper   ObjectMapper para parsear la respuesta.
     * @return Cuerpo de la respuesta de la consulta por CUFE.
     * @throws IllegalStateException si la consulta no retorna contenido o el status code está vacío.
     * @throws RuntimeException      si falla la comunicación con el PAC.
     */
    private String consultarPorCufe(Integer IdMovDoc, String cufe, HttpHeaders headers, RestTemplate template, ObjectMapper mapper) throws Exception {
        ObjectNode consultaJson = mapper.createObjectNode();
        consultaJson.put("sendmail", true);
        consultaJson.put("sendmailtome", true);
        consultaJson.put("is_payroll", false);
        consultaJson.put("is_eqdoc", false);

        String urlConsulta = String.format("https://api.gihos-sas.com/api/ubl2.1/status/document/%s", cufe);
        HttpEntity<String> consultaEntity = new HttpEntity<>(
            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(consultaJson), headers
        );

        log.info("Consultando por CUFE: {}", cufe);
        @SuppressWarnings("null")
        ResponseEntity<String> respuestaConsulta = template.postForEntity(urlConsulta, consultaEntity, String.class);

        String consultaString = respuestaConsulta.getBody();
        if (consultaString == null || consultaString.isEmpty()) {
            throw new IllegalStateException("La consulta por CUFE no retornó contenido");
        }

        JsonNode consultaNode    = mapper.readTree(consultaString);
        byte[] xmlConsultaByte   = Base64.getDecoder().decode(consultaNode.path("attacheddocument").asText());
        String statusCodeValidado = consultaNode.path("ResponseDian").path("Envelope").path("Body")
                                        .path("GetStatusResponse").path("GetStatusResult").path("StatusCode").asText();

        if (statusCodeValidado == null || statusCodeValidado.isBlank()) {
            throw new IllegalStateException("El Status Code de la consulta está vacío");
        }

        String qr      = String.format("https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=%s", cufe);
        byte[] qrBytes = qr.getBytes(StandardCharsets.UTF_8);

        actualizarFacturaMovimientos(3, xmlConsultaByte, qrBytes, cufe, IdMovDoc);

        return consultaString;
    }

    // -------------------------------------------------------------------------
    // DESCARGAR DOCUMENTO PDF
    // -------------------------------------------------------------------------

    /**
     * Descarga el PDF de una factura desde SSRS usando autenticación NTLM.
     *
     * @param IdMovDoc Identificador del movimiento documento.
     * @param Url      URL base del reporte SSRS (puede contener espacios).
     * @return Bytes del PDF descargado.
     * @throws IllegalArgumentException si la URL no puede ser codificada.
     * @throws RuntimeException         si el servidor SSRS retorna un error o falla la conexión.
     */
    public byte[] descargarDocumento(String IdMovDoc, String Url) throws Exception {
        String urlFormateada;
        try {
            urlFormateada = new URI(Url.replace(" ", "%20")).toString();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Error al codificar URL: " + ex.getMessage(), ex);
        }

        String url = String.format("%s&rs:Command=Render&rs:Format=PDF&IdMovDoc=%s", urlFormateada, IdMovDoc);
        log.info("Descargando PDF desde: {}", url);

        try (CloseableHttpClient httpClient = servidorUtil.crearHttpClientConNTLM()) {
            HttpGet request = new HttpGet(url);
            try (CloseableHttpResponse httpResponse = httpClient.execute(request)) {
                int statusCode = httpResponse.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    return EntityUtils.toByteArray(httpResponse.getEntity());
                }
                String errorContent = httpResponse.getEntity() != null
                    ? EntityUtils.toString(httpResponse.getEntity()) : "";
                throw new RuntimeException(httpResponse.getStatusLine().getReasonPhrase() + "\nDetalle: " + errorContent);
            }
        }
    }

    // -------------------------------------------------------------------------
    // DESCARGAR PAQUETE COMPLETO (ZIP)
    // -------------------------------------------------------------------------

    /**
     * Genera un archivo ZIP en memoria con el XML y el PDF de la factura.
     *
     * @param IdMovDoc Identificador del movimiento documento.
     * @param Url      URL base del reporte SSRS para descargar el PDF.
     * @return ArchivoDescarga con el nombre y los bytes del ZIP.
     * @throws IllegalStateException si no se encuentra el XML o el PDF.
     * @throws RuntimeException      si ocurre un error al generar el ZIP.
     */
    public ArchivoDescarga descargarPaqueteCompleto(Integer IdMovDoc, String Url) throws Exception {
        Map<String, String> mapContenido = obtenerNombreAsunto(IdMovDoc);
        String nombreArchivo = mapContenido.get("nombreArchivo");

        byte[] xml = obtenerXml(IdMovDoc);
        if (xml == null || xml.length == 0) {
            throw new IllegalStateException("No se encontró XML para ese IdMovDoc");
        }

        byte[] pdfBytes = descargarDocumento(IdMovDoc.toString(), Url);
        if (pdfBytes == null) {
            throw new IllegalStateException("No se encontró PDF para ese IdMovDoc");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(nombreArchivo + ".xml"));
            zos.write(xml);
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(nombreArchivo + ".pdf"));
            zos.write(pdfBytes);
            zos.closeEntry();
        }

        return new ArchivoDescarga(nombreArchivo, baos.toByteArray());
    }

    // -------------------------------------------------------------------------
    // ENVIAR CORREO
    // -------------------------------------------------------------------------

    /**
     * Envía por correo la factura como ZIP adjunto,
     * configurando el SMTP desde los parámetros de la base de datos.
     *
     * @param IdMovDoc  Identificador del movimiento documento.
     * @param CorreoA   Dirección del destinatario principal.
     * @param CorreoCC  Dirección en copia (puede ser nula o vacía).
     * @param CorreoCCO Dirección en copia oculta (puede ser nula o vacía).
     * @param Url       URL base del reporte SSRS para generar el adjunto.
     * @throws SQLException          si ocurre un error al obtener parámetros SMTP.
     * @throws IllegalArgumentException si no se encuentran parámetros de correo.
     * @throws RuntimeException      si falla el envío del correo.
     */
    @SuppressWarnings("null")
    public void enviarCorreo(int IdMovDoc, String CorreoA, String CorreoCC, String CorreoCCO, String Url) throws Exception {
        Map<String, String> mapContenido = obtenerNombreAsunto(IdMovDoc);
        String asunto        = mapContenido.get("asunto");
        String nombreArchivo = mapContenido.get("nombreArchivo");

        ArchivoDescarga zip = descargarPaqueteCompleto(IdMovDoc, Url);

        String usuario, contraseña, host, puerto, remitente;
        String sql = """
            SELECT * FROM DBO.ParametrosServidor PS
            INNER JOIN DBO.Empresas E ON E.IdEmpresaGrupo = PS.IdEmpresaGrupo
            INNER JOIN DBO.MovimientoDocumentos MD ON MD.IdEmpresaKey = E.IdEmpresaKey
            WHERE PS.NomParametro LIKE 'FE%' AND MD.IdMovDoc = ?
        """;

        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"));
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, IdMovDoc);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, String> parametros = new HashMap<>();
                while (rs.next()) parametros.put(rs.getString("NomParametro"), rs.getString("ValorParametro"));
                if (parametros.isEmpty()) throw new IllegalArgumentException("No se encontraron parámetros de correo para IdMovDoc=" + IdMovDoc);
                usuario    = parametros.get("FE_CorreoOrigen_Direccion");
                contraseña = parametros.get("FE_CorreoOrigen_Pwd");
                host       = parametros.get("FE_CorreoOrigen_Host");
                puerto     = parametros.get("FE_CorreoOrigen_Puerto");
                remitente  = parametros.get("FE_CorreoOrigen_Direccion");
            }
        }

        try {
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost(host);
            mailSender.setPort(Integer.parseInt(puerto));
            mailSender.setUsername(usuario);
            mailSender.setPassword(contraseña);

            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setSubject(asunto);
            helper.setText("Estimado(a) cliente Adjunto encontrará su factura/nota electrónica en formato xml y la representación gráfica en formato pdf.");
            helper.addAttachment(nombreArchivo + ".zip", new ByteArrayResource(zip.getZipBytes()), "application/zip");
            helper.setFrom(remitente);
            helper.setTo(CorreoA);
            if (CorreoCC != null && !CorreoCC.isBlank()) helper.setCc(CorreoCC);
            if (CorreoCCO != null && !CorreoCCO.isBlank()) helper.setBcc(CorreoCCO);

            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Error al enviar el correo: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // PERSISTENCIA
    // -------------------------------------------------------------------------

    /**
     * Actualiza los campos de XML, QR y CUFE en MovimientoDocumentos.
     *
     * @param idDocXmlEnviadoDian Estado de envío a la DIAN.
     * @param xmlBytes            XML de la factura electrónica en bytes.
     * @param qrBytes             Cadena QR en bytes.
     * @param cufe                Código Único de Factura Electrónica.
     * @param IdMovDoc            Identificador del movimiento documento.
     * @throws SQLException si ocurre un error al ejecutar el UPDATE.
     */
    private void actualizarFacturaMovimientos(Integer idDocXmlEnviadoDian, byte[] xmlBytes, byte[] qrBytes, String cufe, Integer IdMovDoc) throws SQLException{
        
        String sqlUpdate = """
            UPDATE MovimientoDocumentos 
            SET 
                DocXmlGenerado = ?,
                DocXmlEnviadoDian = ?,
                DocXmlEnvelope = ?,
                CodigoQR = ?,
                CUFE = ?
            WHERE IdMovDoc = ?
        """;

        try (Connection connUpdate = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"));
            PreparedStatement ps = connUpdate.prepareStatement(sqlUpdate)) {
            ps.setInt(1, 1);
            ps.setInt(2, idDocXmlEnviadoDian);
            ps.setBytes(3, xmlBytes);
            ps.setBytes(4, qrBytes);
            ps.setString(5, cufe);
            ps.setInt(6, IdMovDoc);
            ps.executeUpdate();
        }
    }

    /**
     * Inserta un registro de solicitud en MovimientoDocumentos_FE_Solicitudes.
     *
     * @param IdMovDoc    Identificador del movimiento documento.
     * @param erroresDian Errores retornados por la DIAN.
     * @param xmlBytes    XML de respuesta en bytes.
     * @throws SQLException si ocurre un error al ejecutar el INSERT.
     */
    private void insertarMovimientoFactura(Integer IdMovDoc, String erroresDian, byte[] xmlBytes) throws SQLException{
        String insertSql = "INSERT INTO MovimientoDocumentos_FE_Solicitudes (IdMovDoc, IdTipoSolicitud, FechaSolicitud, EstadoSolicitud, EstadoValidacion, DescripcionEstado, RespXml) VALUES (?, ?, GETDATE(), ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"))){
            try(PreparedStatement ps = conn.prepareStatement(insertSql)){
                ps.setInt(1, IdMovDoc);
                ps.setInt(2, 1);
                ps.setInt(3, 1);
                ps.setInt(4, 1);
                ps.setString(5, erroresDian);
                ps.setBytes(6, xmlBytes);
                ps.executeUpdate();

            }
        }
    }


    // -------------------------------------------------------------------------
    // HELPERS PRIVADOS
    // -------------------------------------------------------------------------

    private byte[] obtenerXml(Integer IdMovDoc) throws SQLException {
        String sql = "SELECT DocXmlEnvelope FROM MovimientoDocumentos WHERE IdMovDoc = ?";
        try (Connection connUpdate = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"));
            PreparedStatement ps = connUpdate.prepareStatement(sql)) {
            ps.setInt(1, IdMovDoc);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                byte[] xmlBytes = rs.getBytes("DocXmlEnvelope");
                if (xmlBytes == null || xmlBytes.length == 0) {
                    return null;
                }
                return xmlBytes;
            }
        }
    }


    private Map<String, String> obtenerNombreAsunto(Integer IdMovDoc) throws SQLException {
        String sql = "SELECT strAsunto, strNomAdjunto FROM dbo.fn_FE_AsuntoCorreo(?)";
        try (Connection conn = DriverManager.getConnection(databaseConfig.getConnectionUrl("IPSoftFinanciero_ST"));
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, IdMovDoc);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("fn_FE_AsuntoCorreo no retornó datos para IdMovDoc=" + IdMovDoc);
                return Map.of("asunto", rs.getString("strAsunto"), "nombreArchivo", rs.getString("strNomAdjunto"));
            }
        }
    }

    private String extraerErroresDian(JsonNode jsonBody) {
        JsonNode errorArray = jsonBody.path("ResponseDian").path("Envelope").path("Body")
            .path("SendBillSyncResponse").path("SendBillSyncResult").path("ErrorMessage").path("string");
        if (errorArray.isArray() && errorArray.size() > 0) {
            return StreamSupport.stream(errorArray.spliterator(), false).map(JsonNode::asText).collect(Collectors.joining("\n"));
        } else if (errorArray.isTextual()) {
            return errorArray.asText();
        }
        return "";
    }

    @SuppressWarnings("null")
    private HttpHeaders construirHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearerToken);
        headers.set("User-Agent", "PostmanRuntime/7.44.1");
        headers.set("Accept", "application/json");
        headers.set("Connection", "keep-alive");
        headers.set("Accept-Encoding", "gzip, deflate, br");
        return headers;
    }
}
