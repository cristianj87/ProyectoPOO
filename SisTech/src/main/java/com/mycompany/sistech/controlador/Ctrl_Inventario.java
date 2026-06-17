package com.mycompany.sistech.controlador;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class Ctrl_Inventario {

    // Centralizamos la conexión
    private final String url = "jdbc:mysql://localhost/sistemainventario";
    private final String userDB = "root";
    private final String passDB = "";

    /**
     * Registra una entrada o salida manual de inventario y guarda la bitácora.
     * Ideal para ajustes por inventario físico, mermas o recepción de proveedores.
     */
    public boolean registrarMovimiento(String codigoProducto, String tipoMovimiento, int cantidad, String justificacion, String usuario) {
        
        try (Connection conn = DriverManager.getConnection(url, userDB, passDB)) {

            // 1. Desactivamos el Auto-Guardado para proteger la transacción
            conn.setAutoCommit(false);

            try {
                // ==========================================
                // PASO A: ACTUALIZAR EL STOCK EN LA TABLA PRODUCTOS
                // ==========================================
                String sqlStock;
                // Determinamos si vamos a sumar o restar dependiendo del tipo de movimiento
                if (tipoMovimiento.equalsIgnoreCase("ENTRADA")) {
                    sqlStock = "UPDATE productos SET stock = stock + ? WHERE codigo = ?";
                } else {
                    sqlStock = "UPDATE productos SET stock = stock - ? WHERE codigo = ?";
                }

                try (PreparedStatement pstmtStock = conn.prepareStatement(sqlStock)) {
                    pstmtStock.setInt(1, cantidad);
                    pstmtStock.setString(2, codigoProducto);
                    
                    int modificado = pstmtStock.executeUpdate();
                    if (modificado == 0) {
                        // Si da 0, significa que el código no existe en la BD
                        throw new Exception("El producto no existe en el catálogo.");
                    }
                }

                // ==========================================
                // PASO B: GUARDAR LA AUDITORÍA EN LA BITÁCORA
                // ==========================================
                String sqlBitacora = "INSERT INTO movimientos_inventario (codigo_producto, tipo_movimiento, cantidad, justificacion, usuario_responsable) VALUES (?, ?, ?, ?, ?)";
                
                try (PreparedStatement pstmtBitacora = conn.prepareStatement(sqlBitacora)) {
                    pstmtBitacora.setString(1, codigoProducto);
                    pstmtBitacora.setString(2, tipoMovimiento.toUpperCase());
                    pstmtBitacora.setInt(3, cantidad);
                    pstmtBitacora.setString(4, justificacion);
                    pstmtBitacora.setString(5, usuario);
                    pstmtBitacora.executeUpdate();
                }

                // 2. Si ambos pasos fueron exitosos, confirmamos los cambios (Commit)
                conn.commit();
                return true;

            } catch (Exception e) {
                // 3. Si algo falla, revertimos todo para evitar un stock irreal (Rollback)
                conn.rollback();
                System.out.println("Error en el movimiento de inventario (Rollback ejecutado): " + e.getMessage());
                return false;
            } finally {
                // Devolvemos la conexión a su estado normal
                conn.setAutoCommit(true);
            }

        } catch (Exception e) {
            System.out.println("Error de conexión al ajustar inventario: " + e.getMessage());
            return false;
        }
    }
}