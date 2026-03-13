package com.certificadosapi.certificados.service;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.certificadosapi.certificados.config.DatabaseConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class AlmacenService {

    private final DatabaseConfig databaseConfig;
    private static final Logger log = LoggerFactory.getLogger(AlmacenService.class);

    @Autowired 
    public AlmacenService(DatabaseConfig databaseConfig){
        this.databaseConfig = databaseConfig;
    }

    
    /**
     * Inserta una nueva entrada en el almacén.
     * Llama al procedimiento almacenado pa_Alm_Entradas_Insertar y retorna el ID generado.
     *
     * @param fecha       Fecha y hora de la entrada (formato: yyyy-MM-ddTHH:mm)
     * @param origen      Origen de la entrada
     * @param documento   Tipo de documento asociado
     * @param observaciones Observaciones adicionales (opcional)
     * @param usuario     Usuario que registra la entrada
     * @param bodegaKey   ID de la bodega destino
     * @return Mapa con la información de la entrada creada:
     *         Si es exitoso: {"success": true, "entradas_Key": id, "fecha": ..., ...}
     * @throws IllegalArgumentException si algún parámetro requerido es nulo o vacío
     * @throws RuntimeException si ocurre un error SQL o inesperado durante la inserción
     */
    public Map<String, Object> insertarEntrada(String fecha, String origen, String documento,
                                                String observaciones, String usuario, int bodegaKey) {

        log.info("Iniciando insertarEntrada");
        log.debug("Parámetros recibidos: fecha={}, origen={}, documento={}, usuario={}, bodegaKey={}",
                fecha, origen, documento, usuario, bodegaKey);

        if (fecha == null || fecha.isBlank()) {
            throw new IllegalArgumentException("El parámetro 'fecha' es requerido y no puede estar vacío");
        }
        if (origen == null || origen.isBlank()) {
            throw new IllegalArgumentException("El parámetro 'origen' es requerido y no puede estar vacío");
        }
        if (documento == null || documento.isBlank()) {
            throw new IllegalArgumentException("El parámetro 'documento' es requerido y no puede estar vacío");
        }
        if (usuario == null || usuario.isBlank()) {
            throw new IllegalArgumentException("El parámetro 'usuario' es requerido y no puede estar vacío");
        }

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{call pa_Alm_Entradas_Insertar(?, ?, ?, ?, ?, ?, ?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {

                    stmt.setTimestamp(1, Timestamp.valueOf(fecha.replace("T", " ")));
                    stmt.setString(2, origen);
                    stmt.setString(3, documento);
                    stmt.setString(4, observaciones);
                    stmt.setString(5, usuario);
                    stmt.setInt(6, bodegaKey);
                    stmt.registerOutParameter(7, Types.INTEGER);

                    log.info("Parámetros SQL: p1={}, p2={}, p3={}, p4={}, p5={}, p6={}",
                            fecha, origen, documento, observaciones, usuario, bodegaKey);

                    stmt.execute();

                    int entradasKey = stmt.getInt(7);
                    log.info("Entrada registrada exitosamente con ID: {}", entradasKey);

                    return Map.of(
                            "success", true,
                            "message", "Entrada registrada exitosamente",
                            "entradas_Key", entradasKey,
                            "fecha", fecha,
                            "origen", origen,
                            "documento", documento,
                            "bodegasKey", bodegaKey
                    );
                }

            } catch (SQLException e) {
                log.error("Error SQL en insertarEntrada: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error inesperado en insertarEntrada: {}", e.getMessage());
            throw new RuntimeException("Error al insertar la entrada: " + e.getMessage());
        }
    }

    /**
     * Inserta el detalle de una entrada en el almacén.
     * Llama al procedimiento almacenado pa_Alm_EntradasDet_Insertar.
     *
     * @param entradasDetKey ID de la entrada padre
     * @param productosKey   ID del producto
     * @param cantidad       Cantidad del producto
     * @param valorCompra    Valor de compra unitario
     * @param iva            IVA aplicado
     * @return Mapa con la información del detalle creado
     * @throws IllegalArgumentException si algún parámetro requerido es nulo o vacío
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public Map<String, Object> insertarEntradaDetalle(int entradasDetKey, int productosKey,
                                                    String cantidad, String valorCompra, String iva) {

        log.info("Iniciando insertarEntradaDetalle");
        log.debug("Parámetros recibidos: entradasDetKey={}, productosKey={}, cantidad={}, valorCompra={}, iva={}",
                entradasDetKey, productosKey, cantidad, valorCompra, iva);

        if (cantidad == null || cantidad.isBlank()) {
            throw new IllegalArgumentException("El parámetro 'cantidad' es requerido y no puede estar vacío");
        }
        if (valorCompra == null || valorCompra.isBlank()) {
            throw new IllegalArgumentException("El parámetro 'valorCompra' es requerido y no puede estar vacío");
        }
        if (iva == null || iva.isBlank()) {
            throw new IllegalArgumentException("El parámetro 'iva' es requerido y no puede estar vacío");
        }

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{call pa_Alm_EntradasDet_Insertar(?, ?, ?, ?, ?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {

                    stmt.setInt(1, entradasDetKey);
                    stmt.setInt(2, productosKey);
                    stmt.setBigDecimal(3, new BigDecimal(cantidad));
                    stmt.setBigDecimal(4, new BigDecimal(valorCompra));
                    stmt.setBigDecimal(5, new BigDecimal(iva));

                    log.debug("Parámetros SQL: p1={}, p2={}, p3={}, p4={}, p5={}",
                            entradasDetKey, productosKey, cantidad, valorCompra, iva);

                    stmt.execute();

                    log.info("Detalle de entrada registrado exitosamente para entradasDetKey={}", entradasDetKey);

                    return Map.of(
                            "success", true,
                            "message", "Detalle de entrada registrado exitosamente",
                            "entradasDet_Key", entradasDetKey,
                            "productos_Key", productosKey,
                            "cantidad", cantidad,
                            "valorCompra", valorCompra
                    );
                }

            } catch (SQLException e) {
                log.error("Error SQL en insertarEntradaDetalle: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error inesperado en insertarEntradaDetalle: {}", e.getMessage());
            throw new RuntimeException("Error al insertar el detalle de entrada: " + e.getMessage());
        }
    }

    /**
     * Lista las entradas del almacén según filtros opcionales.
     * Llama al procedimiento almacenado pa_Alm_Entradas_Listar.
     *
     * @param fechaInicio  Fecha de inicio del rango de búsqueda
     * @param fechaFin     Fecha de fin del rango de búsqueda
     * @param idTerceroKey ID del tercero/proveedor (opcional)
     * @param bodegaKey    ID de la bodega (opcional)
     * @param numeroDesde  Número de documento desde (opcional)
     * @param numeroHasta  Número de documento hasta (opcional)
     * @param tipoDoc      Tipo de documento (opcional)
     * @param origenNombre Nombre del origen (opcional)
     * @return Mapa con {"success": true, "data": [...], "total": n}
     * @throws IllegalArgumentException si las fechas son nulas
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public Map<String, Object> listarEntradas(LocalDate fechaInicio, LocalDate fechaFin,
                                            Integer idTerceroKey, Integer bodegaKey,
                                            Integer numeroDesde, Integer numeroHasta,
                                            String tipoDoc, String origenNombre) {

        log.info("Iniciando listarEntradas");
        log.debug("Parámetros recibidos: fechaInicio={}, fechaFin={}, idTerceroKey={}, bodegaKey={}, numeroDesde={}, numeroHasta={}, tipoDoc={}, origenNombre={}",
                fechaInicio, fechaFin, idTerceroKey, bodegaKey, numeroDesde, numeroHasta, tipoDoc, origenNombre);

        if (fechaInicio == null || fechaFin == null) {
            throw new IllegalArgumentException("Los parámetros 'fechaInicio' y 'fechaFin' son requeridos");
        }

        Integer idTerceroParam  = (idTerceroKey == null || idTerceroKey <= 0)  ? null : idTerceroKey;
        Integer bodegaParam     = (bodegaKey    == null || bodegaKey    <= 0)  ? null : bodegaKey;
        Integer numeroDesdeParam = (numeroDesde == null || numeroDesde  <= 0)  ? null : numeroDesde;
        Integer numeroHastaParam = (numeroHasta == null || numeroHasta  <= 0)  ? null : numeroHasta;
        String  tipoDocParam    = (tipoDoc      == null || tipoDoc.isBlank())  ? null : tipoDoc;
        String  origenParam     = (origenNombre == null || origenNombre.isBlank()) ? null : origenNombre;

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{CALL pa_Alm_Entradas_Listar(?,?,?,?,?,?,?,?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {

                    stmt.setDate(1, Date.valueOf(fechaInicio));
                    stmt.setDate(2, Date.valueOf(fechaFin));
                    stmt.setObject(3, idTerceroParam,  Types.INTEGER);
                    stmt.setObject(4, bodegaParam,     Types.INTEGER);
                    stmt.setObject(5, numeroDesdeParam, Types.INTEGER);
                    stmt.setObject(6, numeroHastaParam, Types.INTEGER);
                    stmt.setObject(7, tipoDocParam,    Types.VARCHAR);
                    stmt.setObject(8, origenParam,     Types.VARCHAR);

                    List<Map<String, Object>> entradas = new ArrayList<>();

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> entrada = new LinkedHashMap<>();
                            entrada.put("Entradas_Key",      rs.getInt("Entradas_Key"));
                            entrada.put("Fecha",             rs.getString("Fecha"));
                            entrada.put("IdTercerokey",      rs.getObject("IdTercerokey"));
                            entrada.put("Proveedor_Nombre",  rs.getString("Proveedor_Nombre"));
                            entrada.put("Tipo",              rs.getString("Tipo"));
                            entrada.put("NumeroDocumento",   rs.getInt("NumeroDocumento"));
                            entrada.put("Bodega_Key",        rs.getInt("Bodega_Key"));
                            entrada.put("Observaciones",     rs.getString("Observaciones"));
                            entrada.put("Usuario",           rs.getString("Usuario"));
                            entrada.put("Anulado",           rs.getBoolean("Anulado"));
                            entrada.put("Contabilizado",     rs.getBoolean("Contabilizado"));
                            entradas.add(entrada);
                        }
                    }

                    log.info("listarEntradas encontró {} registros", entradas.size());

                    return Map.of(
                            "success", true,
                            "data",    entradas,
                            "total",   entradas.size()
                    );
                }

            } catch (SQLException e) {
                log.error("Error SQL en listarEntradas: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error inesperado en listarEntradas: {}", e.getMessage());
            throw new RuntimeException("Error al listar entradas: " + e.getMessage());
        }
    }

    /**
     * Lista los productos del detalle de una entrada específica.
     * Consulta la vista vw_Alm_Entradas_Detalle_Editable.
     *
     * @param entradasKey ID de la entrada
     * @return Mapa con {"success": true, "data": [...], "total": n}
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public Map<String, Object> listarEntradasProducto(int entradasKey) {

        log.info("Iniciando listarEntradasProducto");
        log.debug("Parámetro recibido: entradasKey={}", entradasKey);

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "SELECT * FROM [vw_Alm_Entradas_Detalle_Editable] WHERE Entradas_Key = ?";
                log.debug("SQL ejecutado: {}", sql);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setInt(1, entradasKey);

                    List<Map<String, Object>> productos = new ArrayList<>();

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> producto = new LinkedHashMap<>();
                            producto.put("EntradasDet_Key", rs.getInt("EntradasDet_Key"));
                            producto.put("Entradas_Key",    rs.getInt("Entradas_Key"));
                            producto.put("Productos_Key",   rs.getInt("Productos_Key"));
                            producto.put("ProductoCodigo",  rs.getString("ProductoCodigo"));
                            producto.put("ProductoNombre",  rs.getString("ProductoNombre"));
                            producto.put("Iva",             rs.getInt("Iva"));
                            producto.put("Cantidad",        rs.getBigDecimal("Cantidad"));
                            producto.put("ValorCompra",     rs.getBigDecimal("ValorCompra"));
                            producto.put("TotalRenglon",    rs.getBigDecimal("TotalRenglon"));
                            productos.add(producto);
                        }
                    }

                    log.info("listarEntradasProducto encontró {} productos para entradasKey={}", productos.size(), entradasKey);

                    return Map.of(
                            "success", true,
                            "data",    productos,
                            "total",   productos.size()
                    );
                }

            } catch (SQLException e) {
                log.error("Error SQL en listarEntradasProducto: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error inesperado en listarEntradasProducto: {}", e.getMessage());
            throw new RuntimeException("Error al listar productos de la entrada: " + e.getMessage());
        }
    }

    /**
     * Lista los productos del detalle de una salida específica.
     * Consulta la vista vw_Alm_Salidas_Detalle_Editable.
     *
     * @param salidasKey ID de la salida
     * @return Mapa con {"success": true, "data": [...], "total": n}
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public Map<String, Object> listarSalidasProducto(int salidasKey) {

        log.info("Iniciando listarSalidasProducto");
        log.debug("Parámetro recibido: salidasKey={}", salidasKey);

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "SELECT * FROM [vw_Alm_Salidas_Detalle_Editable] WHERE Salidas_Key = ?";
                log.debug("SQL ejecutado: {}", sql);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setInt(1, salidasKey);

                    List<Map<String, Object>> productos = new ArrayList<>();

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> producto = new LinkedHashMap<>();
                            producto.put("SalidasDet_Key", rs.getInt("SalidasDet_Key"));
                            producto.put("Salidas_Key",    rs.getInt("Salidas_Key"));
                            producto.put("Productos_Key",  rs.getInt("Productos_Key"));
                            producto.put("ProductoCodigo", rs.getString("ProductoCodigo"));
                            producto.put("ProductoNombre", rs.getString("ProductoNombre"));
                            producto.put("Cantidad",       rs.getBigDecimal("Cantidad"));
                            producto.put("CostoUnitario",  rs.getBigDecimal("CostoUnitario"));
                            producto.put("TotalRenglon",   rs.getBigDecimal("TotalRenglon"));
                            productos.add(producto);
                        }
                    }

                    log.info("listarSalidasProducto encontró {} productos para salidasKey={}", productos.size(), salidasKey);

                    return Map.of(
                            "success", true,
                            "data",    productos,
                            "total",   productos.size()
                    );
                }

            } catch (SQLException e) {
                log.error("Error SQL en listarSalidasProducto: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error inesperado en listarSalidasProducto: {}", e.getMessage());
            throw new RuntimeException("Error al listar productos de la salida: " + e.getMessage());
        }
    }

    /**
     * Inserta una nueva salida en el almacén.
     * Llama al procedimiento almacenado pa_Alm_Salidas_Insertar y retorna el ID generado.
     *
     * @param fecha            Fecha y hora de la salida (formato: yyyy-MM-ddTHH:mm)
     * @param destino          Destino de la salida
     * @param documento        Tipo de documento asociado
     * @param observaciones    Observaciones adicionales (opcional)
     * @param usuario          Usuario que registra la salida
     * @param idUnidadAtencion ID de la unidad de atención
     * @param bodegaKey        ID de la bodega origen
     * @return Mapa con la información de la salida creada
     * @throws IllegalArgumentException si algún parámetro requerido es nulo o vacío
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public Map<String, Object> insertarSalida(String fecha, String destino, String documento,
                                            String observaciones, String usuario,
                                            Integer idUnidadAtencion, Integer bodegaKey) {

        log.info("Iniciando insertarSalida");
        log.debug("Parámetros recibidos: fecha={}, destino={}, documento={}, usuario={}, idUnidadAtencion={}, bodegaKey={}",
                fecha, destino, documento, usuario, idUnidadAtencion, bodegaKey);

        if (fecha == null || fecha.isBlank())
            throw new IllegalArgumentException("El parámetro 'fecha' es requerido y no puede estar vacío");
        if (destino == null || destino.isBlank())
            throw new IllegalArgumentException("El parámetro 'destino' es requerido y no puede estar vacío");
        if (documento == null || documento.isBlank())
            throw new IllegalArgumentException("El parámetro 'documento' es requerido y no puede estar vacío");
        if (usuario == null || usuario.isBlank())
            throw new IllegalArgumentException("El parámetro 'usuario' es requerido y no puede estar vacío");

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{call pa_Alm_Salidas_Insertar(?, ?, ?, ?, ?, ?, ?, ?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {

                    stmt.setTimestamp(1, Timestamp.valueOf(fecha.replace("T", " ")));
                    stmt.setString(2, destino);
                    stmt.setString(3, documento);
                    stmt.setString(4, observaciones);
                    stmt.setString(5, usuario);
                    stmt.setInt(6, idUnidadAtencion);
                    stmt.setInt(7, bodegaKey);
                    stmt.registerOutParameter(8, Types.INTEGER);

                    stmt.execute();

                    int salidasKey = stmt.getInt(8);
                    log.info("Salida registrada exitosamente con ID: {}", salidasKey);

                    return Map.of(
                            "success", true,
                            "message", "Salida registrada exitosamente",
                            "salidas_Key", salidasKey,
                            "fecha", fecha,
                            "destino", destino,
                            "documento", documento,
                            "idUnidadAtencion", idUnidadAtencion,
                            "bodegaKey", bodegaKey
                    );
                }

            } catch (SQLException e) {
                log.error("Error SQL en insertarSalida: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error inesperado en insertarSalida: {}", e.getMessage());
            throw new RuntimeException("Error al insertar la salida: " + e.getMessage());
        }
    }

    /**
     * Inserta el detalle de una salida en el almacén.
     * Llama al procedimiento almacenado pa_Alm_SalidasDet_Insertar.
     *
     * @param salidasKey    ID de la salida padre
     * @param productosKey  ID del producto
     * @param cantidad      Cantidad del producto
     * @param costoUnitario Costo unitario del producto
     * @return Mapa con la información del detalle creado
     * @throws IllegalArgumentException si algún parámetro requerido es nulo o vacío
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public Map<String, Object> insertarSalidaDetalle(int salidasKey, int productosKey,
                                                    String cantidad, int costoUnitario) {

        log.info("Iniciando insertarSalidaDetalle");
        log.debug("Parámetros recibidos: salidasKey={}, productosKey={}, cantidad={}, costoUnitario={}",
                salidasKey, productosKey, cantidad, costoUnitario);

        if (cantidad == null || cantidad.isBlank())
            throw new IllegalArgumentException("El parámetro 'cantidad' es requerido y no puede estar vacío");

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{call pa_Alm_SalidasDet_Insertar(?, ?, ?, ?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {

                    stmt.setInt(1, salidasKey);
                    stmt.setInt(2, productosKey);
                    stmt.setBigDecimal(3, new BigDecimal(cantidad));
                    stmt.setBigDecimal(4, new BigDecimal(costoUnitario));

                    stmt.execute();

                    log.info("Detalle de salida registrado exitosamente para salidasKey={}", salidasKey);

                    return Map.of(
                            "success", true,
                            "message", "Detalle de salida registrado exitosamente",
                            "salidas_Key", salidasKey,
                            "productos_Key", productosKey,
                            "cantidad", cantidad,
                            "costoUnitario", costoUnitario
                    );
                }

            } catch (SQLException e) {
                log.error("Error SQL en insertarSalidaDetalle: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error inesperado en insertarSalidaDetalle: {}", e.getMessage());
            throw new RuntimeException("Error al insertar el detalle de salida: " + e.getMessage());
        }
    }

    /**
     * Lista las salidas del almacén según filtros opcionales.
     * Llama al procedimiento almacenado pa_Alm_Salidas_Listar.
     *
     * @param fechaInicio  Fecha de inicio del rango de búsqueda
     * @param fechaFin     Fecha de fin del rango de búsqueda
     * @param idUnidadKey  ID de la unidad de atención (opcional)
     * @param bodegaKey    ID de la bodega (opcional)
     * @param numeroDesde  Número de documento desde (opcional)
     * @param numeroHasta  Número de documento hasta (opcional)
     * @param tipoDoc      Tipo de documento (opcional)
     * @return Mapa con {"success": true, "data": [...], "total": n}
     * @throws IllegalArgumentException si las fechas son nulas
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public Map<String, Object> listarSalidas(LocalDate fechaInicio, LocalDate fechaFin,
                                            Integer idUnidadKey, Integer bodegaKey,
                                            Integer numeroDesde, Integer numeroHasta,
                                            String tipoDoc) {

        log.info("Iniciando listarSalidas");
        log.debug("Parámetros recibidos: fechaInicio={}, fechaFin={}, idUnidadKey={}, bodegaKey={}, numeroDesde={}, numeroHasta={}, tipoDoc={}",
                fechaInicio, fechaFin, idUnidadKey, bodegaKey, numeroDesde, numeroHasta, tipoDoc);

        if (fechaInicio == null || fechaFin == null)
            throw new IllegalArgumentException("Los parámetros 'fechaInicio' y 'fechaFin' son requeridos");

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{CALL pa_Alm_Salidas_Listar(?,?,?,?,?,?,?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement cs = conn.prepareCall(sql)) {

                    cs.setDate(1, Date.valueOf(fechaInicio));
                    cs.setDate(2, Date.valueOf(fechaFin));
                    cs.setObject(3, idUnidadKey,  Types.INTEGER);
                    cs.setObject(4, bodegaKey,    Types.INTEGER);
                    cs.setObject(5, numeroDesde,  Types.INTEGER);
                    cs.setObject(6, numeroHasta,  Types.INTEGER);
                    cs.setObject(7, (tipoDoc != null && !tipoDoc.isBlank()) ? tipoDoc : null, Types.NVARCHAR);

                    List<Map<String, Object>> salidas = new ArrayList<>();

                    try (ResultSet rs = cs.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> salida = new LinkedHashMap<>();
                            salida.put("salidas_Key",       rs.getInt("Salidas_Key"));
                            salida.put("fecha",             rs.getTimestamp("Fecha").toString());
                            salida.put("idUnidadAtencion",  rs.getInt("IdUnidadAtencion"));
                            salida.put("destino",           rs.getString("Destino"));
                            salida.put("tipo",              rs.getString("Tipo"));
                            salida.put("numeroDocumento",   rs.getInt("NumeroDocumento"));
                            salida.put("bodegaKey",         rs.getInt("Bodega_Key"));
                            salida.put("observaciones",     rs.getString("Observaciones"));
                            salida.put("usuario",           rs.getString("Usuario"));
                            salida.put("anulado",           rs.getBoolean("Anulado"));
                            salida.put("contabilizado",     rs.getBoolean("Contabilizado"));
                            salidas.add(salida);
                        }
                    }

                    log.info("listarSalidas encontró {} registros", salidas.size());

                    return Map.of(
                            "success", true,
                            "data",    salidas,
                            "total",   salidas.size()
                    );
                }

            } catch (SQLException e) {
                log.error("Error SQL en listarSalidas: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error inesperado en listarSalidas: {}", e.getMessage());
            throw new RuntimeException("Error al listar salidas: " + e.getMessage());
        }
    }

    /**
     * Inserta un nuevo producto en el almacén.
     * Llama al procedimiento almacenado pa_Alm_Productos_Insertar.
     *
     * @param codigo       Código del producto
     * @param nombre       Nombre del producto
     * @param unidadMedida Unidad de medida
     * @param categoriasKey ID de la categoría
     * @param stockMinimo  Stock mínimo requerido
     * @return Mapa con la información del producto creado
     * @throws IllegalArgumentException si algún parámetro requerido es nulo o vacío
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public Map<String, Object> insertarProducto(String codigo, String nombre, String unidadMedida,
                                                int categoriasKey, String stockMinimo) {

        log.info("Iniciando insertarProducto");
        log.debug("Parámetros recibidos: codigo={}, nombre={}, unidadMedida={}, categoriasKey={}, stockMinimo={}",
                codigo, nombre, unidadMedida, categoriasKey, stockMinimo);

        if (codigo == null || codigo.isBlank())
            throw new IllegalArgumentException("El parámetro 'codigo' es requerido y no puede estar vacío");
        if (nombre == null || nombre.isBlank())
            throw new IllegalArgumentException("El parámetro 'nombre' es requerido y no puede estar vacío");
        if (unidadMedida == null || unidadMedida.isBlank())
            throw new IllegalArgumentException("El parámetro 'unidadMedida' es requerido y no puede estar vacío");
        if (stockMinimo == null || stockMinimo.isBlank())
            throw new IllegalArgumentException("El parámetro 'stockMinimo' es requerido y no puede estar vacío");

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{call pa_Alm_Productos_Insertar(?, ?, ?, ?, ?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {

                    stmt.setString(1, codigo);
                    stmt.setString(2, nombre);
                    stmt.setString(3, unidadMedida);
                    stmt.setInt(4, categoriasKey);
                    stmt.setString(5, stockMinimo);

                    stmt.execute();

                    log.info("Producto registrado exitosamente: codigo={}", codigo);

                    return Map.of(
                            "success", true,
                            "message", "Producto registrado exitosamente",
                            "codigo", codigo,
                            "nombre", nombre,
                            "unidadMedida", unidadMedida,
                            "categorias_Key", categoriasKey,
                            "stockMinimo", stockMinimo
                    );
                }

            } catch (SQLException e) {
                log.error("Error SQL en insertarProducto: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error inesperado en insertarProducto: {}", e.getMessage());
            throw new RuntimeException("Error al insertar el producto: " + e.getMessage());
        }
    }

    /**
     * Lista los productos del almacén según filtros opcionales.
     * Llama al procedimiento almacenado pa_Alm_Productos_Listado.
     *
     * @param nombre       Nombre del producto a filtrar (opcional)
     * @param codigo       Código del producto a filtrar (opcional)
     * @param categoriasKey ID de la categoría (opcional, -1 para ignorar)
     * @param estado       Estado activo/inactivo (opcional)
     * @return Mapa con {"success": true, "data": [...], "total": n}
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public Map<String, Object> listarProductos(String nombre, String codigo,
                                                Integer categoriasKey, Boolean estado) {

        log.info("Iniciando listarProductos");
        log.debug("Parámetros recibidos: nombre={}, codigo={}, categoriasKey={}, estado={}",
                nombre, codigo, categoriasKey, estado);

        String nombreParam    = (nombre == null || nombre.isBlank())   ? null : nombre;
        String codigoParam    = (codigo == null || codigo.isBlank())   ? null : codigo;
        Integer categoriaParam = (categoriasKey == null || categoriasKey <= 0) ? null : categoriasKey;

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{CALL pa_Alm_Productos_Listado(?,?,?,?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {

                    stmt.setObject(1, nombreParam,     Types.VARCHAR);
                    stmt.setObject(2, codigoParam,     Types.VARCHAR);
                    stmt.setObject(3, categoriaParam,  Types.INTEGER);
                    stmt.setObject(4, estado,          Types.BIT);

                    List<Map<String, Object>> productos = new ArrayList<>();

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> producto = new LinkedHashMap<>();
                            producto.put("Productos_Key",   rs.getInt("Productos_Key"));
                            producto.put("Codigo",          rs.getString("Codigo"));
                            producto.put("Nombre",          rs.getString("Nombre"));
                            producto.put("UnidadMedida",    rs.getString("UnidadMedida"));
                            producto.put("NombreCategoria", rs.getString("NombreCategoria"));
                            producto.put("ControlStock",    rs.getString("ControlStock"));
                            producto.put("StockMinimo",     rs.getString("StockMinimo"));
                            producto.put("CategoriasKey",   rs.getString("Categorias_Key"));
                            producto.put("Activo",          rs.getBoolean("activo"));
                            productos.add(producto);
                        }
                    }

                    log.info("listarProductos encontró {} registros", productos.size());

                    return Map.of(
                            "success", true,
                            "data",    productos,
                            "total",   productos.size()
                    );
                }

            } catch (SQLException e) {
                log.error("Error SQL en listarProductos: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error inesperado en listarProductos: {}", e.getMessage());
            throw new RuntimeException("Error al listar productos: " + e.getMessage());
        }
    }

    /**
     * Actualiza un producto existente en el almacén.
     * Llama al procedimiento almacenado pa_Alm_Productos_Actualizar.
     *
     * @param productoKey   ID del producto a actualizar
     * @param codigo        Código del producto
     * @param nombre        Nombre del producto
     * @param unidadMedida  Unidad de medida
     * @param categoriasKey ID de la categoría
     * @param stockMinimo   Stock mínimo requerido
     * @param activo        Estado activo/inactivo
     * @return Mapa con la información del producto actualizado
     * @throws IllegalArgumentException si algún parámetro requerido es nulo o vacío
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public Map<String, Object> actualizarProducto(int productoKey, String codigo, String nombre,
                                                String unidadMedida, int categoriasKey,
                                                String stockMinimo, boolean activo) {

        log.info("Iniciando actualizarProducto");
        log.debug("Parámetros recibidos: productoKey={}, codigo={}, nombre={}, unidadMedida={}, categoriasKey={}, stockMinimo={}, activo={}",
                productoKey, codigo, nombre, unidadMedida, categoriasKey, stockMinimo, activo);

        if (codigo == null || codigo.isBlank())
            throw new IllegalArgumentException("El parámetro 'codigo' es requerido y no puede estar vacío");
        if (nombre == null || nombre.isBlank())
            throw new IllegalArgumentException("El parámetro 'nombre' es requerido y no puede estar vacío");
        if (unidadMedida == null || unidadMedida.isBlank())
            throw new IllegalArgumentException("El parámetro 'unidadMedida' es requerido y no puede estar vacío");
        if (stockMinimo == null || stockMinimo.isBlank())
            throw new IllegalArgumentException("El parámetro 'stockMinimo' es requerido y no puede estar vacío");

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{call pa_Alm_Productos_Actualizar(?, ?, ?, ?, ?, ?, ?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {

                    stmt.setInt(1, productoKey);
                    stmt.setString(2, codigo);
                    stmt.setString(3, nombre);
                    stmt.setString(4, unidadMedida);
                    stmt.setInt(5, categoriasKey);
                    stmt.setString(6, stockMinimo);
                    stmt.setBoolean(7, activo);

                    stmt.execute();

                    log.info("Producto actualizado exitosamente: productoKey={}", productoKey);

                    return Map.of(
                            "success", true,
                            "message", "Producto actualizado exitosamente",
                            "productoKey", productoKey,
                            "codigo", codigo,
                            "nombre", nombre,
                            "unidadMedida", unidadMedida,
                            "categoriasKey", categoriasKey,
                            "stockMinimo", stockMinimo,
                            "activo", activo
                    );
                }

            } catch (SQLException e) {
                log.error("Error SQL en actualizarProducto: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error inesperado en actualizarProducto: {}", e.getMessage());
            throw new RuntimeException("Error al actualizar el producto: " + e.getMessage());
        }
    }

    /**
     * Desactiva un producto del almacén.
     * Llama al procedimiento almacenado pa_Alm_Productos_Desactivar.
     *
     * @param productoKey ID del producto a desactivar
     * @return Mapa con confirmación de la operación
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public Map<String, Object> desactivarProducto(int productoKey) {

        log.info("Iniciando desactivarProducto");
        log.debug("Parámetro recibido: productoKey={}", productoKey);

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{call pa_Alm_Productos_Desactivar(?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {

                    stmt.setInt(1, productoKey);
                    stmt.execute();

                    log.info("Producto desactivado exitosamente: productoKey={}", productoKey);

                    return Map.of(
                            "success", true,
                            "message", "Producto desactivado exitosamente",
                            "productoKey", productoKey
                    );
                }

            } catch (SQLException e) {
                log.error("Error SQL en desactivarProducto: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error inesperado en desactivarProducto: {}", e.getMessage());
            throw new RuntimeException("Error al desactivar el producto: " + e.getMessage());
        }
    }

    /**
     * Inserta una nueva categoría en el almacén.
     * Llama al procedimiento almacenado pa_Alm_Categorias_Insertar.
     *
     * @param codigo      Código de la categoría
     * @param nombre      Nombre de la categoría
     * @param descripcion Descripción de la categoría
     * @return Mapa con la información de la categoría creada
     * @throws IllegalArgumentException si algún parámetro requerido es nulo o vacío
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public Map<String, Object> insertarCategoria(String codigo, String nombre, String descripcion) {

        log.info("Iniciando insertarCategoria");
        log.debug("Parámetros recibidos: codigo={}, nombre={}, descripcion={}", codigo, nombre, descripcion);

        if (codigo == null || codigo.isBlank())
            throw new IllegalArgumentException("El parámetro 'codigo' es requerido y no puede estar vacío");
        if (nombre == null || nombre.isBlank())
            throw new IllegalArgumentException("El parámetro 'nombre' es requerido y no puede estar vacío");
        if (descripcion == null || descripcion.isBlank())
            throw new IllegalArgumentException("El parámetro 'descripcion' es requerido y no puede estar vacío");

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{call pa_Alm_Categorias_Insertar(?, ?, ?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {

                    stmt.setString(1, codigo);
                    stmt.setString(2, nombre);
                    stmt.setString(3, descripcion);

                    stmt.execute();

                    log.info("Categoría registrada exitosamente: codigo={}", codigo);

                    return Map.of(
                            "success", true,
                            "message", "Categoría registrada exitosamente",
                            "codigo", codigo,
                            "nombre", nombre,
                            "descripcion", descripcion
                    );
                }

            } catch (SQLException e) {
                log.error("Error SQL en insertarCategoria: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error inesperado en insertarCategoria: {}", e.getMessage());
            throw new RuntimeException("Error al insertar la categoría: " + e.getMessage());
        }
    }

    /**
     * Lista las categorías del almacén según filtros opcionales.
     * Llama al procedimiento almacenado pa_Alm_Categorias_Listado.
     *
     * @param nombre         Nombre a filtrar (opcional)
     * @param codigo         Código a filtrar (opcional)
     * @param cuentaContable Cuenta contable a filtrar (opcional)
     * @param estado         Estado activo/inactivo (opcional)
     * @return Mapa con {"success": true, "data": [...], "total": n}
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public Map<String, Object> listarCategorias(String nombre, String codigo,
                                                String cuentaContable, Boolean estado) {

        log.info("Iniciando listarCategorias");
        log.debug("Parámetros recibidos: nombre={}, codigo={}, cuentaContable={}, estado={}",
                nombre, codigo, cuentaContable, estado);

        String nombreParam         = (nombre == null || nombre.isBlank())               ? null : nombre;
        String codigoParam         = (codigo == null || codigo.isBlank())               ? null : codigo;
        String cuentaContableParam = (cuentaContable == null || cuentaContable.isBlank()) ? null : cuentaContable;

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{CALL pa_Alm_Categorias_Listado(?,?,?,?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {

                    stmt.setObject(1, nombreParam,         Types.VARCHAR);
                    stmt.setObject(2, codigoParam,         Types.VARCHAR);
                    stmt.setObject(3, cuentaContableParam, Types.VARCHAR);
                    stmt.setObject(4, estado,              Types.BIT);

                    List<Map<String, Object>> categorias = new ArrayList<>();

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> categoria = new LinkedHashMap<>();
                            categoria.put("CategoriasKey",   rs.getString("Categorias_Key"));
                            categoria.put("Codigo",          rs.getString("Codigo"));
                            categoria.put("Nombre",          rs.getString("Nombre"));
                            categoria.put("Descripcion",     rs.getString("Descripcion"));
                            categoria.put("CuentaContable",  rs.getString("CuentaContable"));
                            categoria.put("Estado",          rs.getBoolean("Activo"));
                            categorias.add(categoria);
                        }
                    }

                    log.info("listarCategorias encontró {} registros", categorias.size());

                    return Map.of(
                            "success", true,
                            "data",    categorias,
                            "total",   categorias.size()
                    );
                }

            } catch (SQLException e) {
                log.error("Error SQL en listarCategorias: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error inesperado en listarCategorias: {}", e.getMessage());
            throw new RuntimeException("Error al listar categorías: " + e.getMessage());
        }
    }

    /**
     * Actualiza una categoría existente en el almacén.
     * Llama al procedimiento almacenado pa_Alm_Categorias_Actualizar.
     *
     * @param categoriasKey ID de la categoría a actualizar
     * @param codigo        Código de la categoría
     * @param nombre        Nombre de la categoría
     * @param descripcion   Descripción de la categoría
     * @param activo        Estado activo/inactivo
     * @return Mapa con la información de la categoría actualizada
     * @throws IllegalArgumentException si algún parámetro requerido es nulo o vacío
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public Map<String, Object> actualizarCategoria(int categoriasKey, String codigo, String nombre,
                                                    String descripcion, boolean activo) {

        log.info("Iniciando actualizarCategoria");
        log.debug("Parámetros recibidos: categoriasKey={}, codigo={}, nombre={}, descripcion={}, activo={}",
                categoriasKey, codigo, nombre, descripcion, activo);

        if (codigo == null || codigo.isBlank())
            throw new IllegalArgumentException("El parámetro 'codigo' es requerido y no puede estar vacío");
        if (nombre == null || nombre.isBlank())
            throw new IllegalArgumentException("El parámetro 'nombre' es requerido y no puede estar vacío");
        if (descripcion == null || descripcion.isBlank())
            throw new IllegalArgumentException("El parámetro 'descripcion' es requerido y no puede estar vacío");

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{call pa_Alm_Categorias_Actualizar(?, ?, ?, ?, ?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {

                    stmt.setInt(1, categoriasKey);
                    stmt.setString(2, codigo);
                    stmt.setString(3, nombre);
                    stmt.setString(4, descripcion);
                    stmt.setBoolean(5, activo);

                    stmt.execute();

                    log.info("Categoría actualizada exitosamente: categoriasKey={}", categoriasKey);

                    return Map.of(
                            "success", true,
                            "message", "Categoría actualizada exitosamente",
                            "categoriasKey", categoriasKey,
                            "codigo", codigo,
                            "nombre", nombre,
                            "descripcion", descripcion,
                            "activo", activo
                    );
                }

            } catch (SQLException e) {
                log.error("Error SQL en actualizarCategoria: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error inesperado en actualizarCategoria: {}", e.getMessage());
            throw new RuntimeException("Error al actualizar la categoría: " + e.getMessage());
        }
    }

    /**
     * Desactiva una categoría del almacén.
     * Llama al procedimiento almacenado pa_Alm_Categorias_Desactivar.
     *
     * @param categoriaKey ID de la categoría a desactivar
     * @return Mapa con confirmación de la operación
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public Map<String, Object> desactivarCategoria(int categoriaKey) {

        log.info("Iniciando desactivarCategoria");
        log.debug("Parámetro recibido: categoriaKey={}", categoriaKey);

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{call pa_Alm_Categorias_Desactivar(?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {

                    stmt.setInt(1, categoriaKey);
                    stmt.execute();

                    log.info("Categoría desactivada exitosamente: categoriaKey={}", categoriaKey);

                    return Map.of(
                            "success", true,
                            "message", "Categoría desactivada exitosamente",
                            "categoriaKey", categoriaKey
                    );
                }

            } catch (SQLException e) {
                log.error("Error SQL en desactivarCategoria: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Error inesperado en desactivarCategoria: {}", e.getMessage());
            throw new RuntimeException("Error al desactivar la categoría: " + e.getMessage());
        }
    }

    /**
     * Consulta datos generales del almacén según el tipo de tabla solicitado.
     * Llama al procedimiento almacenado pa_Alm_Generales_tabla.
     *
     * @param Tipo Tipo de tabla a consultar
     * @param Id   Identificador principal (opcional, default 0)
     * @param Id2  Identificador secundario (opcional, default 0)
     * @return ArrayNode con los datos obtenidos
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public ArrayNode consultarGenerales(int Tipo, int Id, int Id2) {

        log.info("Iniciando consultarGenerales");
        log.debug("Parámetros recibidos: Tipo={}, Id={}, Id2={}", Tipo, Id, Id2);

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            ObjectMapper mapper = new ObjectMapper();

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{call pa_Alm_Generales_tabla(?, ?, ?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {

                    stmt.setInt(1, Tipo);
                    stmt.setInt(2, Id);
                    stmt.setInt(3, Id2);

                    boolean hasResults = stmt.execute();
                    ArrayNode dataArray = mapper.createArrayNode();

                    if (hasResults) {
                        try (ResultSet rs = stmt.getResultSet()) {

                            ResultSetMetaData metaData = rs.getMetaData();
                            int columnCount = metaData.getColumnCount();

                            while (rs.next()) {
                                ObjectNode row = mapper.createObjectNode();

                                for (int i = 1; i <= columnCount; i++) {
                                    String columnName = metaData.getColumnName(i);
                                    int columnType = metaData.getColumnType(i);

                                    switch (columnType) {
                                        case Types.INTEGER:
                                        case Types.SMALLINT:
                                        case Types.TINYINT:
                                            row.put(columnName, rs.getInt(i));
                                            break;
                                        case Types.BIGINT:
                                            row.put(columnName, rs.getLong(i));
                                            break;
                                        case Types.DECIMAL:
                                        case Types.NUMERIC:
                                            row.put(columnName, rs.getBigDecimal(i));
                                            break;
                                        case Types.DOUBLE:
                                        case Types.FLOAT:
                                            row.put(columnName, rs.getDouble(i));
                                            break;
                                        case Types.BIT:
                                        case Types.BOOLEAN:
                                            row.put(columnName, rs.getBoolean(i));
                                            break;
                                        case Types.DATE:
                                        case Types.TIMESTAMP:
                                            if (rs.getTimestamp(i) != null) {
                                                row.put(columnName, rs.getTimestamp(i).toString());
                                            } else {
                                                row.putNull(columnName);
                                            }
                                            break;
                                        default:
                                            String value = rs.getString(i);
                                            if (value != null) {
                                                row.put(columnName, value);
                                            } else {
                                                row.putNull(columnName);
                                            }
                                            break;
                                    }
                                }

                                dataArray.add(row);
                            }
                        }
                    }

                    log.info("consultarGenerales retornó {} registros", dataArray.size());
                    return dataArray;
                }

            } catch (SQLException e) {
                log.error("Error SQL en consultarGenerales: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage(), e);
            }

        } catch (Exception e) {
            log.error("Error inesperado en consultarGenerales: {}", e.getMessage());
            throw new RuntimeException("Error al consultar generales: " + e.getMessage(), e);
        }
    }

    /**
     * Actualiza el detalle de una entrada en el almacén.
     * Llama al procedimiento almacenado pa_Alm_EntradasDet_Actualizar.
     *
     * @param EntradasDetKey ID del detalle de entrada a actualizar
     * @param ProductosKey   ID del producto (opcional)
     * @param Cantidad       Cantidad del producto (opcional)
     * @param ValorCompra    Valor de compra (opcional)
     * @param Iva            IVA aplicado (opcional)
     * @throws IllegalArgumentException si EntradasDetKey es inválido
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public void actualizarEntradaDetalle(int EntradasDetKey, Integer ProductosKey,
                                          String Cantidad, String ValorCompra, Double Iva) {

        log.info("Iniciando actualizarEntradaDetalle");
        log.debug("Parámetros recibidos: EntradasDetKey={}, ProductosKey={}, Cantidad={}, ValorCompra={}, Iva={}",
                EntradasDetKey, ProductosKey, Cantidad, ValorCompra, Iva);

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{call pa_Alm_EntradasDet_Actualizar(?, ?, ?, ?, ?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {

                    stmt.setInt(1, EntradasDetKey);

                    if (ProductosKey != null) {
                        stmt.setInt(2, ProductosKey);
                    } else {
                        stmt.setNull(2, Types.INTEGER);
                    }

                    if (Cantidad != null && !Cantidad.isEmpty()) {
                        Cantidad = Cantidad.replace(",", ".");
                        stmt.setBigDecimal(3, new BigDecimal(Cantidad));
                    } else {
                        stmt.setNull(3, Types.DECIMAL);
                    }

                    if (ValorCompra != null && !ValorCompra.isEmpty()) {
                        ValorCompra = ValorCompra.replace(",", ".");
                        stmt.setBigDecimal(4, new BigDecimal(ValorCompra));
                    } else {
                        stmt.setNull(4, Types.DECIMAL);
                    }

                    if (Iva != null) {
                        stmt.setDouble(5, Iva);
                    } else {
                        stmt.setNull(5, Types.DOUBLE);
                    }

                    stmt.execute();
                    log.info("Detalle de entrada actualizado exitosamente: EntradasDetKey={}", EntradasDetKey);
                }

            } catch (SQLException e) {
                log.error("Error SQL en actualizarEntradaDetalle: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage(), e);
            }

        } catch (Exception e) {
            log.error("Error inesperado en actualizarEntradaDetalle: {}", e.getMessage());
            throw new RuntimeException("Error al actualizar detalle de entrada: " + e.getMessage(), e);
        }
    }

        /**
     * Elimina el detalle de una entrada del almacén.
     * Llama al procedimiento almacenado pa_Alm_EntradasDet_Eliminar.
     *
     * @param EntradasDetKey ID del detalle de entrada a eliminar
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public void eliminarEntradaDetalle(int EntradasDetKey) {

        log.info("Iniciando eliminarEntradaDetalle");
        log.debug("Parámetros recibidos: EntradasDetKey={}", EntradasDetKey);

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{call pa_Alm_EntradasDet_Eliminar(?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {
                    stmt.setInt(1, EntradasDetKey);
                    stmt.execute();
                    log.info("Entrada eliminada exitosamente: EntradasDetKey={}", EntradasDetKey);
                }

            } catch (SQLException e) {
                log.error("Error SQL en eliminarEntradaDetalle: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage(), e);
            }

        } catch (Exception e) {
            log.error("Error inesperado en eliminarEntradaDetalle: {}", e.getMessage());
            throw new RuntimeException("Error al eliminar detalle de entrada: " + e.getMessage(), e);
        }
    }

    /**
     * Elimina el detalle de una salida del almacén.
     * Llama al procedimiento almacenado pa_Alm_SalidasDet_Eliminar.
     *
     * @param SalidasDetKey ID del detalle de salida a eliminar
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public void eliminarSalidaDetalle(int SalidasDetKey) {

        log.info("Iniciando eliminarSalidaDetalle");
        log.debug("Parámetros recibidos: SalidasDetKey={}", SalidasDetKey);

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{call pa_Alm_SalidasDet_Eliminar(?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {
                    stmt.setInt(1, SalidasDetKey);
                    stmt.execute();
                    log.info("Salida eliminada exitosamente: SalidasDetKey={}", SalidasDetKey);
                }

            } catch (SQLException e) {
                log.error("Error SQL en eliminarSalidaDetalle: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage(), e);
            }

        } catch (Exception e) {
            log.error("Error inesperado en eliminarSalidaDetalle: {}", e.getMessage());
            throw new RuntimeException("Error al eliminar detalle de salida: " + e.getMessage(), e);
        }
    }

    /**
     * Actualiza el detalle de una salida en el almacén.
     * Llama al procedimiento almacenado pa_Alm_SalidasDet_Actualizar.
     *
     * @param SalidasDetKey  ID del detalle de salida a actualizar
     * @param ProductosKey   ID del producto (opcional)
     * @param Cantidad       Cantidad del producto (opcional)
     * @param CostoUnitario  Costo unitario del producto (opcional)
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public void actualizarSalidaDetalle(int SalidasDetKey, Integer ProductosKey,
                                         String Cantidad, String CostoUnitario) {

        log.info("Iniciando actualizarSalidaDetalle");
        log.debug("Parámetros recibidos: SalidasDetKey={}, ProductosKey={}, Cantidad={}, CostoUnitario={}",
                SalidasDetKey, ProductosKey, Cantidad, CostoUnitario);

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{call pa_Alm_SalidasDet_Actualizar(?, ?, ?, ?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {

                    stmt.setInt(1, SalidasDetKey);

                    if (ProductosKey != null) {
                        stmt.setInt(2, ProductosKey);
                    } else {
                        stmt.setNull(2, Types.INTEGER);
                    }

                    if (Cantidad != null && !Cantidad.isEmpty()) {
                        Cantidad = Cantidad.replace(",", ".");
                        stmt.setBigDecimal(3, new BigDecimal(Cantidad));
                    } else {
                        stmt.setNull(3, Types.DECIMAL);
                    }

                    if (CostoUnitario != null && !CostoUnitario.isEmpty()) {
                        CostoUnitario = CostoUnitario.replace(",", ".");
                        stmt.setBigDecimal(4, new BigDecimal(CostoUnitario));
                    } else {
                        stmt.setNull(4, Types.DECIMAL);
                    }

                    stmt.execute();
                    log.info("Detalle de salida actualizado exitosamente: SalidasDetKey={}", SalidasDetKey);
                }

            } catch (SQLException e) {
                log.error("Error SQL en actualizarSalidaDetalle: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage(), e);
            }

        } catch (Exception e) {
            log.error("Error inesperado en actualizarSalidaDetalle: {}", e.getMessage());
            throw new RuntimeException("Error al actualizar detalle de salida: " + e.getMessage(), e);
        }
    }

    /**
     * Lista el kardex de un producto en el almacén según los filtros indicados.
     * Llama al procedimiento almacenado pa_Alm_Kardex_Listado.
     *
     * @param Productos_Key    ID del producto
     * @param FechaIni         Fecha de inicio del rango
     * @param FechaFin         Fecha de fin del rango
     * @param Tipo             Tipo de movimiento
     * @param Documento        Tipo de documento
     * @param NumeroDocumento  Número del documento
     * @return ArrayNode con los registros del kardex
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public ArrayNode listarKardex(Integer Productos_Key, LocalDate FechaIni, LocalDate FechaFin,
                                   String Tipo, String Documento, Integer NumeroDocumento) {

        log.info("Iniciando listarKardex");
        log.debug("Parámetros recibidos: Productos_Key={}, FechaIni={}, FechaFin={}, Tipo={}, Documento={}, NumeroDocumento={}",
                Productos_Key, FechaIni, FechaFin, Tipo, Documento, NumeroDocumento);

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            ObjectMapper mapper = new ObjectMapper();

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{CALL pa_Alm_Kardex_Listado(?,?,?,?,?,?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {

                    stmt.setObject(1, Productos_Key, Types.INTEGER);
                    stmt.setObject(2, Date.valueOf(FechaIni), Types.DATE);
                    stmt.setObject(3, Date.valueOf(FechaFin), Types.DATE);
                    stmt.setObject(4, Tipo, Types.VARCHAR);
                    stmt.setObject(5, Documento, Types.VARCHAR);
                    stmt.setObject(6, NumeroDocumento, Types.INTEGER);

                    ArrayNode entradas = mapper.createArrayNode();

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            ObjectNode entrada = mapper.createObjectNode();
                            entrada.put("Kardex_Key", rs.getInt("Kardex_Key"));
                            entrada.put("Fecha", rs.getString("Fecha"));
                            entrada.put("Productos_Key", rs.getInt("Productos_Key"));
                            entrada.put("Bodega_Key", rs.getInt("Bodega_Key"));
                            entrada.put("CodigoProducto", rs.getString("CodigoProducto"));
                            entrada.put("NombreProducto", rs.getString("NombreProducto"));
                            entrada.put("Tipo", rs.getString("Tipo"));
                            entrada.put("Documento", rs.getString("Documento"));
                            entrada.put("NumeroDocumento", rs.getString("NumeroDocumento"));
                            entrada.put("Cantidad", rs.getBigDecimal("Cantidad"));
                            entrada.put("CostoUnitarioAnt", rs.getBigDecimal("CostoUnitarioAnt"));
                            entrada.put("SaldoCostoAnt", rs.getBigDecimal("SaldoCostoAnt"));
                            entrada.put("NuevoSaldoCant", rs.getBigDecimal("NuevoSaldoCant"));
                            entrada.put("NuevoCostoUnit", rs.getBigDecimal("NuevoCostoUnit"));
                            entrada.put("NuevoSaldoCosto", rs.getBigDecimal("NuevoSaldoCosto"));
                            entrada.put("Usuario", rs.getString("Usuario"));
                            entradas.add(entrada);
                        }
                    }

                    log.info("listarKardex retornó {} registros", entradas.size());
                    return entradas;
                }

            } catch (SQLException e) {
                log.error("Error SQL en listarKardex: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage(), e);
            }

        } catch (Exception e) {
            log.error("Error inesperado en listarKardex: {}", e.getMessage());
            throw new RuntimeException("Error al listar kardex: " + e.getMessage(), e);
        }
    }

    /**
     * Lista las existencias del almacén según los filtros indicados.
     * Llama al procedimiento almacenado Pa_Alm_Existencias.
     *
     * @param Productos  IDs de productos separados por coma (opcional)
     * @param Bodegas    IDs de bodegas separados por coma (opcional)
     * @param Categoria  ID de la categoría (opcional)
     * @return ArrayNode con las existencias encontradas
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public ArrayNode listarExistencias(String Productos, String Bodegas, Integer Categoria) {

        log.info("Iniciando listarExistencias");
        log.debug("Parámetros recibidos: Productos={}, Bodegas={}, Categoria={}", Productos, Bodegas, Categoria);

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            ObjectMapper mapper = new ObjectMapper();

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "{CALL Pa_Alm_Existencias(?,?,?)}";
                log.debug("SQL ejecutado: {}", sql);

                try (CallableStatement stmt = conn.prepareCall(sql)) {

                    stmt.setString(1, Productos);
                    stmt.setString(2, Bodegas);

                    if (Categoria != null) {
                        stmt.setInt(3, Categoria);
                    } else {
                        stmt.setNull(3, Types.INTEGER);
                    }

                    ArrayNode data = mapper.createArrayNode();

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            ObjectNode row = mapper.createObjectNode();
                            row.put("Productos_Key", rs.getInt("Productos_Key"));
                            row.put("Codigo", rs.getString("Codigo"));
                            row.put("Nombre", rs.getString("Nombre"));
                            row.put("UnidadMedida", rs.getString("UnidadMedida"));
                            row.put("Categorias_Key", rs.getInt("Categorias_Key"));
                            row.put("Bodegas_Key", rs.getInt("Bodegas_Key"));
                            row.put("CantidadActual", rs.getBigDecimal("CantidadActual"));
                            row.put("CostoPromedio", rs.getBigDecimal("CostoPromedio"));
                            row.put("ValorInventario", rs.getBigDecimal("ValorInventario"));
                            data.add(row);
                        }
                    }

                    log.info("listarExistencias retornó {} registros", data.size());
                    return data;
                }

            } catch (SQLException e) {
                log.error("Error SQL en listarExistencias: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage(), e);
            }

        } catch (Exception e) {
            log.error("Error inesperado en listarExistencias: {}", e.getMessage());
            throw new RuntimeException("Error al listar existencias: " + e.getMessage(), e);
        }
    }

    /**
     * Contabiliza una entrada del almacén marcándola como contabilizada.
     * Ejecuta un UPDATE directo sobre la tabla Alm_Entradas.
     *
     * @param entradasKey ID de la entrada a contabilizar
     * @throws IllegalStateException si no se pudo actualizar el registro
     * @throws RuntimeException si ocurre un error SQL o inesperado
     */
    public void contabilizarEntrada(Integer entradasKey) {

        log.info("Iniciando contabilizarEntrada");
        log.debug("Parámetros recibidos: entradasKey={}", entradasKey);

        try {
            String connectionUrl = databaseConfig.getConnectionUrl("IPSoftFinanciero_ST");
            log.debug("Conectando a BD: {}", connectionUrl);

            try (Connection conn = DriverManager.getConnection(connectionUrl)) {

                String sql = "UPDATE Alm_Entradas SET Contabilizado = 1 WHERE Entradas_Key = ?";
                log.debug("SQL ejecutado: {}", sql);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, entradasKey);
                    int filas = stmt.executeUpdate();

                    if (filas == 0) {
                        log.warn("No se encontró entrada para contabilizar: entradasKey={}", entradasKey);
                        throw new IllegalStateException("No se pudo contabilizar la entrada para: " + entradasKey);
                    }

                    log.info("Entrada contabilizada exitosamente: entradasKey={}", entradasKey);
                }

            } catch (SQLException e) {
                log.error("Error SQL en contabilizarEntrada: {}", e.getMessage());
                throw new RuntimeException("Error de base de datos: " + e.getMessage(), e);
            }

        } catch (Exception e) {
            log.error("Error inesperado en contabilizarEntrada: {}", e.getMessage());
            throw new RuntimeException("Error al contabilizar entrada: " + e.getMessage(), e);
        }
    }

}

