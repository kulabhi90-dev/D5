package com.example;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Enumeration;

@WebListener
public class AppListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        sce.getServletContext().log("D5 application started.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Close the connection pool first
        Db.close();

        // Deregister only the JDBC drivers loaded by this web app (prevents Tomcat leak warnings)
        ClassLoader webappLoader = getClass().getClassLoader();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() == webappLoader) {
                try {
                    DriverManager.deregisterDriver(driver);
                } catch (Exception e) {
                    sce.getServletContext().log("Error deregistering JDBC driver " + driver, e);
                }
            }
        }
    }
}
