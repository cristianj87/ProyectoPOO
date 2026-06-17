package com.mycompany.sistech.controlador;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class Ctrl_Ventas {

    private final String url = "jdbc:mysql://localhost/sistemainventario";
    private final String userDB = "root";
    private final String passDB = "";

    /**
     * Procesa una venta completa asegurando la integridad de los datos.
     * Utiliza un arreglo JSON simple para recibir los productos desde el HTML.
     */
    public int procesarNuevaVenta(String cliente, double totalVenta, String[][] productosVendidos) {
        
        // El bloque try-with-resources asegura que la conexión se cierre al terminar
        try (Connection conn = DriverManager.getConnection(url, userDB, passDB)) {

            // PASO CLAVE: Desactivamos el "Auto-Guardado". 
            // Esto significa que nada se guardará permanentemente hasta que nosotros demos la orden.
            // Es como un borrador. Si algo falla, podemos cancelar todo (Rollback).
            conn.setAutoCommit(false);

            try {
                // ==========================================
                // 1. REGISTRAR LA CABECERA DE LA VENTA
                // ==========================================
                String sqlVenta = "INSERT INTO ventas (cliente, total, fecha) VALUES (?, ?, NOW())";
                
                // Usamos RETURN_GENERATED_KEYS para saber qué número de "Ticket" (ID) se acaba de crear
                int idVentaGenerada;
                try (PreparedStatement stmtVenta = conn.prepareStatement(sqlVenta, Statement.RETURN_GENERATED_KEYS)) {
                    stmtVenta.setString(1, cliente);
                    stmtVenta.setDouble(2, totalVenta);
                    stmtVenta.executeUpdate();

                    // Obtenemos el ID de la venta recién creada
                    try (ResultSet rsKeys = stmtVenta.getGeneratedKeys()) {
                        if (rsKeys.next()) {
                            idVentaGenerada = rsKeys.getInt(1);
                        } else {
                            throw new Exception("No se pudo obtener el ID de la venta.");
                        }
                    }
                }

                // ==========================================
                // 2. REGISTRAR EL DETALLE Y DESCONTAR INVENTARIO
                // ==========================================
                String sqlDetalle = "INSERT INTO detalle_ventas (id_venta, codigo_producto, cantidad, precio_unitario) VALUES (?, ?, ?, ?)";
                String sqlActualizarStock = "UPDATE productos SET stock = stock - ? WHERE codigo = ?";

                try (PreparedStatement stmtDetalle = conn.prepareStatement(sqlDetalle);
                     PreparedStatement stmtStock = conn.prepareStatement(sqlActualizarStock)) {

                    // Recorremos cada producto que venía en el carrito desde el HTML
                    for (String[] producto : productosVendidos) {
                        String codigo = producto[0];
                        int cantidad = Integer.parseInt(producto[1]);
                        double precio = Double.parseDouble(producto[2]);

                        // A) Guardar en detalle_ventas
                        stmtDetalle.setInt(1, idVentaGenerada);
                        stmtDetalle.setString(2, codigo);
                        stmtDetalle.setInt(3, cantidad);
                        stmtDetalle.setDouble(4, precio);
                        stmtDetalle.executeUpdate();

                        // B) Descontar el stock en la tabla productos
                        stmtStock.setInt(1, cantidad);
                        stmtStock.setString(2, codigo);
                        stmtStock.executeUpdate();
                    }
                }

                // ==========================================
                // 3. CONFIRMAR TODO (COMMIT)
                // ==========================================
                // Si llegamos hasta aquí sin errores, guardamos los cambios permanentemente
                conn.commit();
                return idVentaGenerada;

            } catch (Exception e) {
                conn.rollback();
                System.out.println("Transacción cancelada: " + e.getMessage());
                return -1; // 3. Retornamos -1 para indicar error
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            return -1;
        }
    }
}