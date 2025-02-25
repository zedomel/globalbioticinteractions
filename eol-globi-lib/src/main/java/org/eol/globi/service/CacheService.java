package org.eol.globi.service;

import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.io.IOException;

import static org.eol.globi.service.CacheServiceUtil.createCacheDir;

public class CacheService {

    private File cacheDir = new File("target/term-cache");

    public DB initDb(String cacheName) throws IOException {
        File mapdbCacheDir = getMapDBDir();
        if (!mapdbCacheDir.exists()) {
            createCacheDir(getMapDBDir());
        }
        return getDb(cacheName, mapdbCacheDir);
    }

    private DB getDb(String cacheName, File mapdbCacheDir) {
        File mapDBFile = new File(mapdbCacheDir, cacheName);

        DBMaker dbMaker = DBMaker
                .newFileDB(mapDBFile)
                .mmapFileEnableIfSupported()
                .mmapFileCleanerHackDisable()
                .transactionDisable()
                .closeOnJvmShutdown();
        return dbMaker
                .make();
    }

    private File getMapDBDir() {
        return new File(getCacheDir(), "mapdb");
    }

    public void setCacheDir(File cacheFilename) {
        this.cacheDir = cacheFilename;
    }

    public File getCacheDir() {
        return this.cacheDir;
    }

}
