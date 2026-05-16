package com.dbexport.config;

import com.dbexport.model.DatabaseInfo;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ConfigTests {
    
    @Test
    void testHikariPoolManager_Singleton() {
        HikariPoolManager m1 = HikariPoolManager.getInstance();
        HikariPoolManager m2 = HikariPoolManager.getInstance();
        assertSame(m1, m2);
    }
    
    @Test
    void testHikariPoolManager_PoolStatus_Null() {
        HikariPoolManager mgr = HikariPoolManager.getInstance();
        Map<String, Integer> status = mgr.getPoolStatus(null);
        assertEquals(0, status.get("active"));
        assertEquals(0, status.get("total"));
    }
    
    @Test
    void testHikariPoolManager_ClosePool_Null() {
        HikariPoolManager mgr = HikariPoolManager.getInstance();
        mgr.closePool(null); // should not throw
    }
    
    @Test
    void testHikariPoolManager_CreatePool_MySQL() {
        HikariPoolManager mgr = HikariPoolManager.getInstance();
        DatabaseInfo info = new DatabaseInfo("localhost", 3306, "testdb", "root", "password", "MYSQL");
        HikariDataSource ds = mgr.createPool(info, 2);
        assertNotNull(ds);
        assertEquals(2, ds.getMaximumPoolSize());
        mgr.closePool(ds);
    }
    
    @Test
    void testHikariPoolManager_GetPoolStatus_Closed() {
        HikariPoolManager mgr = HikariPoolManager.getInstance();
        DatabaseInfo info = new DatabaseInfo("localhost", 3306, "testdb", "root", "password", "MYSQL");
        HikariDataSource ds = mgr.createPool(info, 2);
        mgr.closePool(ds);
        Map<String, Integer> status = mgr.getPoolStatus(ds);
        assertEquals(0, status.get("active"));
    }
}
