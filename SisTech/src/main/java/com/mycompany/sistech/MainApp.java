package com.mycompany.sistech;
import javafx.print.PrinterJob;
import com.mycompany.sistech.controlador.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javafx.application.Application;
import javafx.application.Platform; // Importante para cambiar la pantalla
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

public class MainApp extends Application {

    private WebEngine motorWeb;
    // 1. Instanciamos el puente UNA SOLA VEZ para que conserve los datos (como el nombre de usuario)
    private BackendBridge puenteBackend;

    @Override
    public void start(Stage primaryStage) {
        WebView navegador = new WebView();
        motorWeb = navegador.getEngine();
        
        // Inicializamos el puente pasando el motor
        puenteBackend = new BackendBridge(motorWeb);

        String rutaLogin = getClass().getResource("/web/login.html").toExternalForm();
        motorWeb.load(rutaLogin);

        motorWeb.getLoadWorker().stateProperty().addListener((observable, estadoViejo, estadoNuevo) -> {
            if (estadoNuevo == Worker.State.SUCCEEDED) {
                JSObject ventanaWeb = (JSObject) motorWeb.executeScript("window");
                // 2. Inyectamos SIEMPRE el mismo puente
                ventanaWeb.setMember("javaBackend", puenteBackend);

                // 3. SOLUCIÓN AL CARGANDO: Si la página que acaba de cargar es el dashboard, 
                // Java le da la orden directa a JavaScript de inicializarse.
                String urlActual = motorWeb.getLocation();
                if (urlActual != null && urlActual.contains("dashboard.html")) {
                    motorWeb.executeScript("iniciarDashboardDesdeJava();");
                }
            }
        });

        Scene escena = new Scene(navegador, 1200, 730);
        primaryStage.setTitle("SisTech - Acceso al Sistema");
        primaryStage.setScene(escena);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    // --- LA CLASE PUENTE ---
    public class BackendBridge {
        private WebEngine motor;
        private String nombreUsuarioActual = "";
        private Ctrl_Producto controlProducto = new Ctrl_Producto();

        public BackendBridge(WebEngine motor) {
            this.motor = motor;
        }

        public boolean validarLogin(String usuario, String password) {
            // Para fines de prueba rápida si la base de datos falla:
            if (usuario.equals("admin") && password.equals("admin")) {
                this.nombreUsuarioActual = "Juan Pérez (Admin)";
                return true;
            }

            String url = "jdbc:mysql://localhost/sistemainventario";
            String userDB = "root";
            String passDB = ""; 
            String passwordCifrada = cifrarMD5(password);

            String sql = "SELECT nombre FROM usuarios WHERE nickname = ? AND password = ?";

            try (Connection conn = DriverManager.getConnection(url, userDB, passDB);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, usuario);
                pstmt.setString(2, passwordCifrada); 

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        // Guardamos el nombre en el estado del puente
                        this.nombreUsuarioActual = rs.getString("nombre");
                        return true; 
                    }
                    return false;
                }
            } catch (Exception e) {
                System.out.println("Error BD: " + e.getMessage());
                return false;
            }
        }

        public String getNombreUsuario() {
            return this.nombreUsuarioActual;
        }
        
        public void cargarDashboard() {
            Platform.runLater(() -> {
                motor.load(getClass().getResource("/web/dashboard.html").toExternalForm());
            });
        }

        public void cargarPantallaLogin() {
            // Al cerrar sesión, limpiamos el nombre
            this.nombreUsuarioActual = "";
            Platform.runLater(() -> {
                motor.load(getClass().getResource("/web/login.html").toExternalForm());
            });
        }
        
        public void cargarPantallaRegistro() {
            Platform.runLater(() -> {
                motor.load(getClass().getResource("/web/registro.html").toExternalForm());
            });
        }

        private Ctrl_Dashboard controlDashboard = new Ctrl_Dashboard();

        // Reemplazamos los métodos antiguos
        public String getDatosVentasJSON() {
            return controlDashboard.obtenerVentasRecientes();
        }

        public String getDatosStockJSON() {
            return controlDashboard.obtenerBajosInventarios();
        }

        private String cifrarMD5(String passwordPlana) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] bytesCifrados = md.digest(passwordPlana.getBytes());
                StringBuilder sb = new StringBuilder();
                for (byte b : bytesCifrados) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (NoSuchAlgorithmException e) {
                return null;
            }
        }     
        
        
       
        public boolean registrarUsuario(String nombre, String apellidos, String nickname, String password) {
            String url = "jdbc:mysql://localhost/sistemainventario";
            String userDB = "root";
            String passDB = ""; 

            // ¡AQUÍ ESTÁ LA MAGIA! Ciframos la contraseña antes de guardarla
            String passwordCifrada = cifrarMD5(password);

            String sql = "INSERT INTO usuarios (nombre, apellidos, nickname, password) VALUES (?, ?, ?, ?)";

            try (Connection conn = DriverManager.getConnection(url, userDB, passDB);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, nombre);
                pstmt.setString(2, apellidos);
                pstmt.setString(3, nickname);
                // Enviamos el hash MD5 a la base de datos, no el texto plano
                pstmt.setString(4, passwordCifrada); 

                int filasAfectadas = pstmt.executeUpdate();
                return filasAfectadas > 0;

            } catch (Exception e) {
                System.out.println("Error al registrar usuario: " + e.getMessage());
                return false;
            }
        }
        
        // ==========================================
        // MÓDULO DE PRODUCTOS (Delegado)
        // ==========================================

        public boolean guardarNuevoProducto(String codigo, String nombre, String categoria, int stock, double precio) {
            // El puente simplemente le pasa los datos al controlador
            return controlProducto.guardarNuevoProducto(codigo, nombre, categoria, stock, precio);
        }

        public String obtenerListaProductosJSON() {
            // El puente simplemente le pide el JSON al controlador
            return controlProducto.obtenerListaProductosJSON();
        }
        
        // ==========================================
        // MÓDULO DE VENTAS (Puente y parseo)
        // ==========================================
        
        // Clase auxiliar interna para leer el JSON que nos mandará JavaScript
        class ItemCarrito {
            String codigo;
            int cantidad;
            double precio;
        }

        public int registrarVentaDesdeWeb(String nombreCliente, double totalVenta, String carritoJSON) {
            try {
                // 1. Usamos Gson para convertir el texto JSON a un arreglo de objetos Java
                com.google.gson.Gson gson = new com.google.gson.Gson();
                ItemCarrito[] items = gson.fromJson(carritoJSON, ItemCarrito[].class);

                // 2. Transformamos ese arreglo al formato de matriz (String[][]) que pide nuestro Ctrl_Ventas
                String[][] matrizProductos = new String[items.length][3];
                for (int i = 0; i < items.length; i++) {
                    matrizProductos[i][0] = items[i].codigo;
                    matrizProductos[i][1] = String.valueOf(items[i].cantidad);
                    matrizProductos[i][2] = String.valueOf(items[i].precio);
                }

                // 3. Delegamos el trabajo pesado al controlador
                Ctrl_Ventas controlVentas = new Ctrl_Ventas();
                return controlVentas.procesarNuevaVenta(nombreCliente, totalVenta, matrizProductos);

            } catch (Exception e) {
                System.out.println("Error al procesar el JSON del carrito: " + e.getMessage());
                return -1;
            }
        }
        
        // Instanciamos el controlador de inventario
        private Ctrl_Inventario controlInventario = new Ctrl_Inventario();

        // Método que llamará JavaScript al presionar "Ajustar Inventario"
        public boolean ajustarInventario(String codigo, String tipo, int cantidad, String justificacion) {
            // Utilizamos el nombre del usuario que inició sesión (que ya teníamos guardado en el puente)
            return controlInventario.registrarMovimiento(codigo, tipo, cantidad, justificacion, this.nombreUsuarioActual);
        }
        
        
        // ==========================================
        // CONTROL DE HARDWARE (Impresora)
        // ==========================================
        // ==========================================
        // CONTROL DE HARDWARE (Impresora)
        // ==========================================
        public void imprimirPantalla() {
            Platform.runLater(() -> {
                PrinterJob job = PrinterJob.createPrinterJob();
                
                if (job != null) {
                    // AL PASAR 'null', LA VENTANA DE IMPRESIÓN SE ABRE SIN PROBLEMAS
                    boolean procederConImpresion = job.showPrintDialog(null);
                    
                    if (procederConImpresion) {
                        motor.print(job);
                        job.endJob();
                    }
                } else {
                    System.out.println("No se detectó ninguna impresora instalada en el sistema.");
                }
            });
        }
        
        
        // Instanciamos el nuevo controlador
        private Ctrl_Usuario controlUsuario = new Ctrl_Usuario();

        // Método que el HTML llamará cuando el usuario guarde sus cambios en la vista de "Configuración"
        public boolean actualizarMiPerfil(String nickname, String nuevoNombre, String nuevosApellidos) {
            return controlUsuario.actualizarDatosUsuario(nickname, nuevoNombre, nuevosApellidos);
        }
    }
}