package com.scienceminer.lookup.storage.lookup;

import com.codahale.metrics.Meter;
import com.scienceminer.lookup.configuration.LookupConfiguration;
import com.scienceminer.lookup.data.MatchingDocument;
import com.scienceminer.lookup.exception.ServiceException;
import com.scienceminer.lookup.exception.ServiceOverloadedException;
import com.scienceminer.lookup.reader.FatcatJsonReader;
import com.scienceminer.lookup.storage.StorageEnvFactory;
import com.scienceminer.lookup.utils.BinarySerialiser;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.lmdbjava.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.scienceminer.lookup.web.resource.DataController.DEFAULT_MAX_SIZE_LIST;
import static java.nio.ByteBuffer.allocateDirect;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.lowerCase;

/**
 * Lookup metadata -> fatcatIdent
 */
public class MetadataLookup {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataLookup.class);

    private Env<ByteBuffer> environment;
    private Dbi<ByteBuffer> dbFatcatJson;
    private Dbi<ByteBuffer> dbDoiToFatcat;

    public static final String ENV_NAME = "fatcat";

    public static final String NAME_FATCAT_JSON = ENV_NAME + "_Jsondoc";
    public static final String NAME_DOI2FATCAT = ENV_NAME + "_doi2fatcat";
    private final int batchSize;

    private LookupConfiguration configuration;

    public MetadataLookup(StorageEnvFactory storageEnvFactory) {
        this.environment = storageEnvFactory.getEnv(ENV_NAME);

        configuration = storageEnvFactory.getConfiguration();
        batchSize = configuration.getBatchSize();
        dbFatcatJson = this.environment.openDbi(NAME_FATCAT_JSON, DbiFlags.MDB_CREATE);
        dbDoiToFatcat = this.environment.openDbi(NAME_DOI2FATCAT, DbiFlags.MDB_CREATE);
    }

    public void loadFromFile(InputStream is, FatcatJsonReader reader, Meter meter) {
        final TransactionWrapper transactionWrapper = new TransactionWrapper(environment.txnWrite());
        final AtomicInteger counter = new AtomicInteger(0);

        reader.load(is, fatcatData -> {
            if (counter.get() == batchSize) {
                transactionWrapper.tx.commit();
                transactionWrapper.tx.close();
                transactionWrapper.tx = environment.txnWrite();
                counter.set(0);
            }
            String key = lowerCase(fatcatData.get("ident").asText());

            store(key, fatcatData.toString(), dbFatcatJson, transactionWrapper.tx);
            // ext_ids is always set; doi is never empty if defined
            if(fatcatData.get("ext_ids").get("doi") != null) {
                store(lowerCase(fatcatData.get("ext_ids").get("doi").asText()), key, dbDoiToFatcat, transactionWrapper.tx);
            }
            meter.mark();
            counter.incrementAndGet();

        });
        transactionWrapper.tx.commit();
        transactionWrapper.tx.close();

        LOGGER.info("Cross checking number of records processed: " + meter.getCount());
    }

    private void store(String key, String value, Dbi<ByteBuffer> db, Txn<ByteBuffer> tx) {
        try {
            final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
            keyBuffer.put(BinarySerialiser.serialize(key)).flip();
            final byte[] serializedValue = BinarySerialiser.serializeAndCompress(value);
            final ByteBuffer valBuffer = allocateDirect(serializedValue.length);
            valBuffer.put(serializedValue).flip();
            db.put(tx, keyBuffer, valBuffer);
        } catch (Exception e) {
            LOGGER.error("Cannot store the entry " + key + ", " + value, e);
        }
    }

    public Map<String, Long> getSize() {

        Map<String, Long> sizes = new HashMap<>();
        try (final Txn<ByteBuffer> txn = this.environment.txnRead()) {
            sizes.put(NAME_FATCAT_JSON, dbFatcatJson.stat(txn).entries);
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        }

        return sizes;
    }

    public String retrieveJsonDocument(String fatcatIdent) {
        final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
        ByteBuffer cachedData = null;
        String record = null;
        try (Txn<ByteBuffer> tx = environment.txnRead()) {
            keyBuffer.put(BinarySerialiser.serialize(fatcatIdent)).flip();
            cachedData = dbFatcatJson.get(tx, keyBuffer);
            if (cachedData != null) {
                record = (String) BinarySerialiser.deserializeAndDecompress(cachedData);
            }
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        } catch (Exception e) {
            LOGGER.error("Cannot retrieve Fatcat document by ident:  " + fatcatIdent, e);
        }

        return record;

    }

    public String retrieveJsonDocumentByDoi(String doi) {
        final ByteBuffer keyBuffer = allocateDirect(environment.getMaxKeySize());
        ByteBuffer cachedData = null;
        String record = null;
        try (Txn<ByteBuffer> tx = environment.txnRead()) {
            keyBuffer.put(BinarySerialiser.serialize(doi)).flip();
            cachedData = dbDoiToFatcat.get(tx, keyBuffer);
            if (cachedData != null) {
                record = (String) BinarySerialiser.deserializeAndDecompress(cachedData);
            }
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        } catch (Exception e) {
            LOGGER.error("Cannot retrieve Fatcat ident by DOI:  " + doi, e);
        }

        return retrieveJsonDocument(record);

    }

    /**
     * Lookup by fatcatIdent
     **/
    public MatchingDocument retrieveByMetadata(String fatcatIdent) {
        if (isBlank(fatcatIdent)) {
            throw new ServiceException(400, "The supplied fatcat ident is null.");
        }
        final String jsonDocument = retrieveJsonDocument(lowerCase(fatcatIdent));

        return new MatchingDocument(fatcatIdent, jsonDocument);
    }

    public MatchingDocument retrieveByMetadataDoi(String doi) {
        if (isBlank(doi)) {
            throw new ServiceException(400, "The supplied DOI is null.");
        }
        final String jsonDocument = retrieveJsonDocumentByDoi(lowerCase(doi));

        return new MatchingDocument(doi, jsonDocument);
    }

    public List<Pair<String, String>> retrieveList(Integer total) {
        return retrieveList(total, dbFatcatJson);
    }

    public List<Pair<String, String>> retrieveList(Integer total, Dbi<ByteBuffer> db) {
        if (total == null || total == 0) {
            total = DEFAULT_MAX_SIZE_LIST;
        }

        List<Pair<String, String>> values = new ArrayList<>();
        int counter = 0;

        try (Txn<ByteBuffer> txn = environment.txnRead()) {
            try (CursorIterator<ByteBuffer> it = db.iterate(txn, KeyRange.all())) {
                for (final CursorIterator.KeyVal<ByteBuffer> kv : it.iterable()) {
                    String key = null;
                    try {
                        key = (String) BinarySerialiser.deserialize(kv.key());
                        values.add(new ImmutablePair<>(key, (String) BinarySerialiser.deserializeAndDecompress(kv.val())));
                    } catch (IOException e) {
                        LOGGER.error("Cannot decompress document with key: " + key, e);
                    }
                    if (counter == total) {
                        txn.close();
                        break;
                    }
                    counter++;
                }
            }
        } catch (Env.ReadersFullException e) {
            throw new ServiceOverloadedException("Not enough readers for LMDB access, increase them or reduce the parallel request rate. ", e);
        }

        return values;
    }
}
