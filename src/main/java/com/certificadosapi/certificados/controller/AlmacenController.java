package com.certificadosapi.certificados.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.certificadosapi.certificados.config.DatabaseConfig;
import com.certificadosapi.certificados.service.AlmacenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/almacen")
@CrossOrigin(origins = "*")
public class AlmacenController {

    private final DatabaseConfig databaseConfig;
    private final AlmacenService almacenService;

    @Autowired 
    public AlmacenController(DatabaseConfig databaseConfig, AlmacenService almacenService){
        this.databaseConfig = databaseConfig;
        this.almacenService = almacenService;
    }

    /**
     * Inserta una nueva entrada en el almacén.
     *
     * @param fecha         Fecha y hora de la entrada (formato: yyyy-MM-ddTHH:mm)
     * @param origen        Origen de la entrada
     * @param documento     Tipo de documento asociado
     * @param observaciones Observaciones adicionales (opcional)
     * @param usuario       Usuario que registra la entrada
     * @param bodegaKey     ID de la bodega destino
     * @return HTTP 200 con la información de la entrada creada
     */
    @GetMapping("/insertarEntrada")
    public ResponseEntity<?> insertarEntrada(
            @RequestParam String fecha,
            @RequestParam String origen,
            @RequestParam String documento,
            @RequestParam(required = false) String observaciones,
            @RequestParam String usuario,
            @RequestParam int bodegaKey) {
        return ResponseEntity.ok(almacenService.insertarEntrada(fecha, origen, documento, observaciones, usuario, bodegaKey));
    }

    /**
     * Inserta el detalle de una entrada en el almacén.
     *
     * @param entradasDetKey ID de la entrada padre
     * @param productosKey   ID del producto
     * @param cantidad       Cantidad del producto
     * @param valorCompra    Valor de compra unitario
     * @param iva            IVA aplicado
     * @return HTTP 200 con la información del detalle creado
     */
    @GetMapping("/InsertarEntradaDetalle")
        public ResponseEntity<?> insertarEntradaDetalle(
            @RequestParam int entradasDetKey,
            @RequestParam int productosKey,
            @RequestParam String cantidad,
            @RequestParam String valorCompra,
            @RequestParam String iva) {
        return ResponseEntity.ok(almacenService.insertarEntradaDetalle(entradasDetKey, productosKey, cantidad, valorCompra, iva));
    }

    /**
     * Lista las entradas del almacén según filtros opcionales.
     *
     * @param fechaInicio  Fecha de inicio del rango de búsqueda
     * @param fechaFin     Fecha de fin del rango de búsqueda
     * @param idTerceroKey ID del tercero/proveedor (opcional)
     * @param bodegaKey    ID de la bodega (opcional)
     * @param numeroDesde  Número de documento desde (opcional)
     * @param numeroHasta  Número de documento hasta (opcional)
     * @param tipoDoc      Tipo de documento (opcional)
     * @param origenNombre Nombre del origen (opcional)
     * @return HTTP 200 con las entradas encontradas
     */
    @GetMapping("/ListarEntradas")
    public ResponseEntity<?> listarEntradas(
            @RequestParam LocalDate fechaInicio,
            @RequestParam LocalDate fechaFin,
            @RequestParam(required = false) Integer idTerceroKey,
            @RequestParam(required = false) Integer bodegaKey,
            @RequestParam(required = false) Integer numeroDesde,
            @RequestParam(required = false) Integer numeroHasta,
            @RequestParam(required = false) String tipoDoc,
            @RequestParam(required = false) String origenNombre) {
        return ResponseEntity.ok(almacenService.listarEntradas(fechaInicio, fechaFin, idTerceroKey, bodegaKey, numeroDesde, numeroHasta, tipoDoc, origenNombre));
    }


    /**
     * Lista los productos del detalle de una entrada específica.
     *
     * @param entradasKey ID de la entrada
     * @return HTTP 200 con los productos del detalle
     */
    @GetMapping("/ListarEntradasProducto")
    public ResponseEntity<?> listarEntradasProducto(@RequestParam int entradasKey) {
        return ResponseEntity.ok(almacenService.listarEntradasProducto(entradasKey));
    }

    /**
     * Lista los productos del detalle de una salida específica.
     *
     * @param salidasKey ID de la salida
     * @return HTTP 200 con los productos del detalle
     */
    @GetMapping("/ListarSalidasProducto")
    public ResponseEntity<?> listarSalidasProducto(@RequestParam int salidasKey) {
        return ResponseEntity.ok(almacenService.listarSalidasProducto(salidasKey));
    }


    @GetMapping("/insertarSalida")
    public ResponseEntity<?> insertarSalida(
            @RequestParam String fecha,
            @RequestParam String destino,
            @RequestParam String documento,
            @RequestParam(required = false) String observaciones,
            @RequestParam String usuario,
            @RequestParam Integer idUnidadAtencion,
            @RequestParam Integer bodegaKey) {
        return ResponseEntity.ok(almacenService.insertarSalida(fecha, destino, documento, observaciones, usuario, idUnidadAtencion, bodegaKey));
    }


    /**
     * Inserta el detalle de una salida en el almacén.
     *
     * @param salidasKey    ID de la salida padre
     * @param productosKey  ID del producto
     * @param cantidad      Cantidad del producto
     * @param costoUnitario Costo unitario del producto
     * @return HTTP 200 con la información del detalle creado
     */
    @GetMapping("/InsertarSalidaDetalle")
    public ResponseEntity<?> insertarSalidaDetalle(
            @RequestParam int salidasKey,
            @RequestParam int productosKey,
            @RequestParam String cantidad,
            @RequestParam int costoUnitario) {
        return ResponseEntity.ok(almacenService.insertarSalidaDetalle(salidasKey, productosKey, cantidad, costoUnitario));
    }

    /**
     * Lista las salidas del almacén según filtros opcionales.
     *
     * @param fechaInicio  Fecha de inicio del rango de búsqueda
     * @param fechaFin     Fecha de fin del rango de búsqueda
     * @param idUnidadKey  ID de la unidad de atención (opcional)
     * @param bodegaKey    ID de la bodega (opcional)
     * @param numeroDesde  Número de documento desde (opcional)
     * @param numeroHasta  Número de documento hasta (opcional)
     * @param tipoDoc      Tipo de documento (opcional)
     * @return HTTP 200 con las salidas encontradas
     */
    @GetMapping("/ListarSalidas")
    public ResponseEntity<?> listarSalidas(
            @RequestParam LocalDate fechaInicio,
            @RequestParam LocalDate fechaFin,
            @RequestParam(required = false) Integer idUnidadKey,
            @RequestParam(required = false) Integer bodegaKey,
            @RequestParam(required = false) Integer numeroDesde,
            @RequestParam(required = false) Integer numeroHasta,
            @RequestParam(required = false) String tipoDoc) {
        return ResponseEntity.ok(almacenService.listarSalidas(fechaInicio, fechaFin, idUnidadKey, bodegaKey, numeroDesde, numeroHasta, tipoDoc));
    }


    /**
     * Inserta un nuevo producto en el almacén.
     *
     * @param codigo        Código del producto
     * @param nombre        Nombre del producto
     * @param unidadMedida  Unidad de medida
     * @param categoriasKey ID de la categoría
     * @param stockMinimo   Stock mínimo requerido
     * @return HTTP 200 con la información del producto creado
     */
    @GetMapping("/InsertarProducto")
    public ResponseEntity<?> insertarProducto(
            @RequestParam String codigo,
            @RequestParam String nombre,
            @RequestParam String unidadMedida,
            @RequestParam int categoriasKey,
            @RequestParam String stockMinimo) {
        return ResponseEntity.ok(almacenService.insertarProducto(codigo, nombre, unidadMedida, categoriasKey, stockMinimo));
    }
    

    /**
     * Lista los productos del almacén según filtros opcionales.
     *
     * @param nombre        Nombre del producto a filtrar (opcional)
     * @param codigo        Código del producto a filtrar (opcional)
     * @param categoriasKey ID de la categoría (opcional)
     * @param estado        Estado activo/inactivo (opcional)
     * @return HTTP 200 con los productos encontrados
     */
    @GetMapping("/ListarProductos")
    public ResponseEntity<?> listarProductos(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String codigo,
            @RequestParam(required = false) Integer categoriasKey,
            @RequestParam(required = false) Boolean estado) {
        return ResponseEntity.ok(almacenService.listarProductos(nombre, codigo, categoriasKey, estado));
    }

    /**
     * Actualiza un producto existente en el almacén.
     *
     * @param productoKey   ID del producto a actualizar
     * @param codigo        Código del producto
     * @param nombre        Nombre del producto
     * @param unidadMedida  Unidad de medida
     * @param categoriasKey ID de la categoría
     * @param stockMinimo   Stock mínimo requerido
     * @param activo        Estado activo/inactivo
     * @return HTTP 200 con la información del producto actualizado
     */
    @PutMapping("/ActualizarProducto")
    public ResponseEntity<?> actualizarProducto(
            @RequestParam int productoKey,
            @RequestParam String codigo,
            @RequestParam String nombre,
            @RequestParam String unidadMedida,
            @RequestParam int categoriasKey,
            @RequestParam String stockMinimo,
            @RequestParam boolean activo) {
        return ResponseEntity.ok(almacenService.actualizarProducto(productoKey, codigo, nombre, unidadMedida, categoriasKey, stockMinimo, activo));
    }

    /**
     * Desactiva un producto del almacén.
     *
     * @param productoKey ID del producto a desactivar
     * @return HTTP 200 si se desactivó correctamente
     */
    @PatchMapping("/DesactivarProducto")
    public ResponseEntity<?> desactivarProducto(@RequestParam int productoKey) {
        return ResponseEntity.ok(almacenService.desactivarProducto(productoKey));
    }

    /**
     * Inserta una nueva categoría en el almacén.
     *
     * @param codigo      Código de la categoría
     * @param nombre      Nombre de la categoría
     * @param descripcion Descripción de la categoría
     * @return HTTP 200 con la información de la categoría creada
     */
    @PostMapping("/InsertarCategoria")
    public ResponseEntity<?> insertarCategoria(
            @RequestParam String codigo,
            @RequestParam String nombre,
            @RequestParam String descripcion) {
        return ResponseEntity.ok(almacenService.insertarCategoria(codigo, nombre, descripcion));
    }

    /**
     * Lista las categorías del almacén según filtros opcionales.
     *
     * @param nombre         Nombre a filtrar (opcional)
     * @param codigo         Código a filtrar (opcional)
     * @param cuentaContable Cuenta contable a filtrar (opcional)
     * @param estado         Estado activo/inactivo (opcional)
     * @return HTTP 200 con las categorías encontradas
     */
    @GetMapping("/ListarCategorias")
    public ResponseEntity<?> listarCategorias(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String codigo,
            @RequestParam(required = false) String cuentaContable,
            @RequestParam(required = false) Boolean estado) {
        return ResponseEntity.ok(almacenService.listarCategorias(nombre, codigo, cuentaContable, estado));
    }

    /**
     * Actualiza una categoría existente en el almacén.
     *
     * @param categoriasKey ID de la categoría a actualizar
     * @param codigo        Código de la categoría
     * @param nombre        Nombre de la categoría
     * @param descripcion   Descripción de la categoría
     * @param activo        Estado activo/inactivo
     * @return HTTP 200 con la información de la categoría actualizada
     */
    @PutMapping("/ActualizarCategoria")
    public ResponseEntity<?> actualizarCategoria(
            @RequestParam int categoriasKey,
            @RequestParam String codigo,
            @RequestParam String nombre,
            @RequestParam String descripcion,
            @RequestParam boolean activo) {
        return ResponseEntity.ok(almacenService.actualizarCategoria(categoriasKey, codigo, nombre, descripcion, activo));
    }

    /**
     * Desactiva una categoría del almacén.
     *
     * @param categoriaKey ID de la categoría a desactivar
     * @return HTTP 200 si se desactivó correctamente
     */
    @PatchMapping("/DesactivarCategoria")
    public ResponseEntity<?> desactivarCategoria(@RequestParam int categoriaKey) {
        return ResponseEntity.ok(almacenService.desactivarCategoria(categoriaKey));
    }

    /**
     * Consulta datos generales del almacén según el tipo de tabla solicitado.
     *
     * @param Tipo Tipo de tabla a consultar
     * @param Id   Identificador principal (opcional, default 0)
     * @param Id2  Identificador secundario (opcional, default 0)
     * @return HTTP 200 con los datos obtenidos
     */
    @GetMapping("/ConsultarGenerales")
    public ResponseEntity<?> consultarGenerales(
            @RequestParam int Tipo,
            @RequestParam(required = false, defaultValue = "0") int Id,
            @RequestParam(required = false, defaultValue = "0") int Id2) {
        return ResponseEntity.ok(almacenService.consultarGenerales(Tipo, Id, Id2));
    }

    /**
     * Actualiza el detalle de una entrada del almacén.
     *
     * @param EntradasDetKey ID del detalle de entrada a actualizar
     * @param ProductosKey   ID del producto (opcional)
     * @param Cantidad       Cantidad del producto (opcional)
     * @param ValorCompra    Valor de compra (opcional)
     * @param Iva            IVA aplicado (opcional)
     * @return HTTP 200 si se actualizó correctamente
     */
    @PutMapping("/ActualizarEntradaDetalle")
    public ResponseEntity<String> actualizarEntradaDetalle(
            @RequestParam int EntradasDetKey,
            @RequestParam(required = false) Integer ProductosKey,
            @RequestParam(required = false) String Cantidad,
            @RequestParam(required = false) String ValorCompra,
            @RequestParam(required = false) Double Iva) {
        almacenService.actualizarEntradaDetalle(EntradasDetKey, ProductosKey, Cantidad, ValorCompra, Iva);
        return ResponseEntity.ok("Detalle de entrada actualizado correctamente");
    }

    /**
     * Elimina el detalle de una entrada del almacén.
     *
     * @param EntradasDetKey ID del detalle de entrada a eliminar
     * @return HTTP 200 si se eliminó correctamente
     */
    @DeleteMapping("/EliminarEntradaDetalle")
    public ResponseEntity<String> eliminarEntradaDetalle(@RequestParam int EntradasDetKey) {
        almacenService.eliminarEntradaDetalle(EntradasDetKey);
        return ResponseEntity.ok("Ítem eliminado correctamente");
    }

    /**
     * Elimina el detalle de una salida del almacén.
     *
     * @param SalidasDetKey ID del detalle de salida a eliminar
     * @return HTTP 200 si se eliminó correctamente
     */
    @DeleteMapping("/EliminarSalidaDetalle")
    public ResponseEntity<String> eliminarSalidaDetalle(@RequestParam int SalidasDetKey) {
        almacenService.eliminarSalidaDetalle(SalidasDetKey);
        return ResponseEntity.ok("Ítem eliminado correctamente");
    }

    /**
     * Actualiza el detalle de una salida del almacén.
     *
     * @param SalidasDetKey  ID del detalle de salida a actualizar
     * @param ProductosKey   ID del producto (opcional)
     * @param Cantidad       Cantidad del producto (opcional)
     * @param CostoUnitario  Costo unitario del producto (opcional)
     * @return HTTP 200 si se actualizó correctamente
     */
    @PutMapping("/ActualizarSalidaDetalle")
    public ResponseEntity<String> actualizarSalidaDetalle(
            @RequestParam int SalidasDetKey,
            @RequestParam(required = false) Integer ProductosKey,
            @RequestParam(required = false) String Cantidad,
            @RequestParam(required = false) String CostoUnitario) {
        almacenService.actualizarSalidaDetalle(SalidasDetKey, ProductosKey, Cantidad, CostoUnitario);
        return ResponseEntity.ok("Detalle de salida actualizado correctamente");
    }

    /**
     * Lista el kardex de un producto según los filtros indicados.
     *
     * @param Productos_Key   ID del producto
     * @param FechaIni        Fecha de inicio del rango
     * @param FechaFin        Fecha de fin del rango
     * @param Tipo            Tipo de movimiento
     * @param Documento       Tipo de documento
     * @param NumeroDocumento Número del documento
     * @return HTTP 200 con los registros del kardex
     */
    @GetMapping("/ListarKardex")
    public ResponseEntity<?> listarKardex(
            @RequestParam Integer Productos_Key,
            @RequestParam LocalDate FechaIni,
            @RequestParam LocalDate FechaFin,
            @RequestParam String Tipo,
            @RequestParam String Documento,
            @RequestParam Integer NumeroDocumento) {
        return ResponseEntity.ok(almacenService.listarKardex(Productos_Key, FechaIni, FechaFin, Tipo, Documento, NumeroDocumento));
    }

    /**
     * Lista las existencias del almacén según los filtros indicados.
     *
     * @param Productos IDs de productos separados por coma (opcional)
     * @param Bodegas   IDs de bodegas separados por coma (opcional)
     * @param Categoria ID de la categoría (opcional)
     * @return HTTP 200 con las existencias encontradas
     */
    @GetMapping("/ListarExistencias")
    public ResponseEntity<?> listarExistencias(
            @RequestParam(required = false) String Productos,
            @RequestParam(required = false) String Bodegas,
            @RequestParam(required = false) Integer Categoria) {
        return ResponseEntity.ok(almacenService.listarExistencias(Productos, Bodegas, Categoria));
    }

    /**
     * Contabiliza una entrada del almacén.
     *
     * @param entradasKey ID de la entrada a contabilizar
     * @return HTTP 200 si se contabilizó correctamente
     */
    @PutMapping("/ContabilizarEntrada")
    public ResponseEntity<String> contabilizarEntrada(@RequestParam Integer entradasKey) {
        almacenService.contabilizarEntrada(entradasKey);
        return ResponseEntity.ok("Entrada contabilizada correctamente");
    }

}