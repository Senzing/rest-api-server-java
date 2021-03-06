package com.senzing.api.services;

import com.senzing.api.model.*;
import com.senzing.g2.engine.G2Engine;
import com.senzing.util.JsonUtils;
import com.senzing.util.SemanticVersion;
import com.senzing.util.Timers;

import javax.json.*;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import java.util.*;

import static com.senzing.api.model.SzHttpMethod.*;
import static com.senzing.api.model.SzFeatureMode.*;
import static com.senzing.api.model.SzRelationshipMode.*;
import static com.senzing.api.model.SzAttributeSearchResultType.*;
import static com.senzing.api.services.ServicesUtil.*;
import static com.senzing.g2.engine.G2Engine.*;
import static javax.ws.rs.core.MediaType.*;

/**
 * Provides entity data related API services.
 */
@Path("/")
@Produces(APPLICATION_JSON)
public class EntityDataServices {
  /**
   * The minimum native API version to support search filtering.
   */
  public static final SemanticVersion MINIMUM_SEARCH_FILTERING_VERSION
      = new SemanticVersion("2.4.1");

  /**
   * The {@link Map} of {@link SzAttributeSearchResultType} keys to {@link
   * Integer} values representing the flags to apply.
   */
  private static final Map<SzAttributeSearchResultType, Integer>
      RESULT_TYPE_FLAG_MAP;

  static {
    Map<SzAttributeSearchResultType, Integer> map = new LinkedHashMap<>();
    map.put(MATCH, G2_EXPORT_INCLUDE_RESOLVED);
    map.put(POSSIBLE_MATCH, G2_EXPORT_INCLUDE_POSSIBLY_SAME);
    map.put(POSSIBLE_RELATION, G2_EXPORT_INCLUDE_POSSIBLY_RELATED);
    map.put(NAME_ONLY_MATCH, G2_EXPORT_INCLUDE_NAME_ONLY);
    RESULT_TYPE_FLAG_MAP = Collections.unmodifiableMap(map);
  }

  @POST
  @Path("data-sources/{dataSourceCode}/records")
  public SzLoadRecordResponse loadRecord(
      @PathParam("dataSourceCode")                    String  dataSourceCode,
      @QueryParam("loadId")                           String  loadId,
      @QueryParam("withInfo") @DefaultValue("false")  boolean withInfo,
      @QueryParam("withRaw")  @DefaultValue("false")  boolean withRaw,
      @Context                                        UriInfo uriInfo,
      String                                                  recordJsonData)
  {
    Timers timers = newTimers();

    try {
      SzApiProvider provider = SzApiProvider.Factory.getProvider();
      dataSourceCode = dataSourceCode.trim().toUpperCase();
      ensureLoadingIsAllowed(provider, POST, uriInfo, timers);

      final String dataSource = dataSourceCode;

      final String normalizedLoadId = normalizeString(loadId);

      String recordText = ensureJsonFields(
          POST,
          uriInfo,
          timers,
          recordJsonData,
          Collections.singletonMap("DATA_SOURCE", dataSource),
          Collections.singletonMap("ENTITY_TYPE", "GENERIC"));

      JsonObject  recordJson    = JsonUtils.parseJsonObject(recordText);
      String      jsonRecordId  = JsonUtils.getString(recordJson, "RECORD_ID");
      if (jsonRecordId != null) {
        if (jsonRecordId.trim().length() == 0) {
          // we have an empty record ID, we need to strip it from the JSON
          JsonObjectBuilder jsonBuilder = Json.createObjectBuilder(recordJson);
          jsonBuilder.remove("RECORD_ID");
          recordJson    = jsonBuilder.build();
          jsonRecordId  = null;
          recordText    = JsonUtils.toJsonText(recordJson);
        }
      }

      final String inRecordId     = jsonRecordId;
      final String recordJsonText = recordText;
      checkDataSource(POST, uriInfo, timers, dataSource, provider);

      StringBuffer sb = new StringBuffer();

      // get the asynchronous info queue
      boolean asyncInfo = provider.hasInfoSink();

      enteringQueue(timers);
      String text = provider.executeInThread(() -> {
        exitingQueue(timers);

        // get the engine API and the config API
        G2Engine engineApi = provider.getEngineApi();

        int result;
        if (withInfo || asyncInfo) {
          callingNativeAPI(timers, "engine", "addRecordWithInfo");
          result = engineApi.addRecordWithInfo(
              dataSource,
              (inRecordId == null) ? "" : inRecordId, // empty record ID
              recordJsonText,
              normalizedLoadId,
              0,
              sb);
          calledNativeAPI(timers, "engine", "addRecordWithInfo");

        } else if (inRecordId == null) {
          callingNativeAPI(timers, "engine", "addRecordWithReturnedRecordID");
          result = engineApi.addRecordWithReturnedRecordID(dataSource,
                                                           sb,
                                                           recordJsonText,
                                                           normalizedLoadId);
          calledNativeAPI(timers, "engine", "addRecordWithReturnedRecordID");

        } else {
          callingNativeAPI(timers, "engine", "addRecord");
          result = engineApi.addRecord(dataSource,
                                       inRecordId,
                                       recordJsonText,
                                       normalizedLoadId);
          calledNativeAPI(timers, "engine", "addRecord");
        }

        if (result != 0) {
          throw newPossiblyNotFoundException(POST, uriInfo, timers, engineApi);
        }

        return sb.toString().trim();
      });

      String            recordId  = inRecordId;
      SzResolutionInfo  info      = null;
      String            rawData   = null;

      if (withInfo || asyncInfo) {
        rawData = text;
        JsonObject jsonObject = JsonUtils.parseJsonObject(rawData);

        // if info was requested or we need to return the record ID then we need
        // to parse the info so we can return it or extract the record ID
        if (withInfo || inRecordId == null) {
          info = SzResolutionInfo.parseResolutionInfo(null, jsonObject);
        }

        // check if the info sink is configured
        if (asyncInfo && rawData != null && rawData.trim().length() > 0) {
          SzMessageSink infoSink = provider.acquireInfoSink();
          SzMessage message = new SzMessage(rawData);
          try {
            // send the info on the async queue
            infoSink.send(message, ServicesUtil::logFailedAsyncInfo);

          } catch (Exception e) {
            // failed async logger will not double-log
            logFailedAsyncInfo(e, message);

          } finally {
            provider.releaseInfoSink(infoSink);
          }
        }

        // if the record ID is generated, we need to return it
        if (inRecordId == null) recordId = info.getRecordId();

        // nullify the info object reference if the info was not requested
        if (!withInfo) info = null;

      } else if (inRecordId == null) {
        recordId = text;
      }

      // construct the response
      SzLoadRecordResponse response = new SzLoadRecordResponse(
          POST, 200, uriInfo, timers, recordId, info);

      if (withRaw && withInfo) {
        response.setRawData(rawData);
      }

      // return the response
      return response;

    } catch (ServerErrorException e) {
      e.printStackTrace();
      throw e;

    } catch (WebApplicationException e) {
      throw e;

    } catch (Exception e) {
      e.printStackTrace();
      throw ServicesUtil.newInternalServerErrorException(POST, uriInfo, timers, e);
    }
  }

  @PUT
  @Path("data-sources/{dataSourceCode}/records/{recordId}")
  public SzLoadRecordResponse loadRecord(
      @PathParam("dataSourceCode")                    String  dataSourceCode,
      @PathParam("recordId")                          String  recordId,
      @QueryParam("loadId")                           String  loadId,
      @QueryParam("withInfo") @DefaultValue("false")  boolean withInfo,
      @QueryParam("withRaw")  @DefaultValue("false")  boolean withRaw,
      @Context                                        UriInfo uriInfo,
      String                                                  recordJsonData)
  {
    Timers timers = newTimers();
    try {
      SzApiProvider provider = SzApiProvider.Factory.getProvider();
      ensureLoadingIsAllowed(provider, PUT, uriInfo, timers);
      dataSourceCode = dataSourceCode.trim().toUpperCase();

      final String dataSource = dataSourceCode;

      final String normalizedLoadId = normalizeString(loadId);

      Map<String,String> map = Map.of("DATA_SOURCE", dataSource,
                                      "RECORD_ID", recordId);

      Map<String,String> defaultMap = Map.of("ENTITY_TYPE", "GENERIC");

      String recordText = ensureJsonFields(PUT,
                                           uriInfo,
                                           timers,
                                           recordJsonData,
                                           map,
                                           defaultMap);

      Set<String> dataSources = provider.getDataSources(dataSource);

      if (!dataSources.contains(dataSource)) {
        throw newNotFoundException(
            PUT, uriInfo, timers,
            "The specified data source is not recognized: " + dataSource);
      }

      // get the asynchronous info sink
      boolean asyncInfo = provider.hasInfoSink();

      enteringQueue(timers);
      String rawInfo = provider.executeInThread(() -> {
        exitingQueue(timers);

        // get the engine API
        G2Engine engineApi = provider.getEngineApi();

        int result;
        String rawData = null;
        if (withInfo || asyncInfo) {
          StringBuffer sb = new StringBuffer();
          callingNativeAPI(timers, "engine", "addRecordWithInfo");
          result = engineApi.addRecordWithInfo(dataSource,
                                               recordId,
                                               recordText,
                                               normalizedLoadId,
                                               0,
                                               sb);
          calledNativeAPI(timers, "engine", "addRecordWithInfo");
          rawData = sb.toString();
        } else {
          callingNativeAPI(timers, "engine", "addRecord");
          result = engineApi.addRecord(dataSource,
                                       recordId,
                                       recordText,
                                       normalizedLoadId);
          calledNativeAPI(timers, "engine", "addRecord");
        }
        if (result != 0) {
          throw newPossiblyNotFoundException(PUT, uriInfo, timers, engineApi);
        }

        return rawData;
      });

      SzResolutionInfo info = null;
      if (rawInfo != null && rawInfo.trim().length() > 0) {
        // check if the info sink is configured
        if (asyncInfo) {
          SzMessageSink infoSink = provider.acquireInfoSink();
          SzMessage message = new SzMessage(rawInfo);
          try {
            // send the info on the async queue
            infoSink.send(message, ServicesUtil::logFailedAsyncInfo);

          } catch (Exception e) {
            logFailedAsyncInfo(e, message);

          } finally {
            provider.releaseInfoSink(infoSink);
          }
        }

        // check if the info was requested
        if (withInfo) {
          JsonObject jsonObject = JsonUtils.parseJsonObject(rawInfo);
          info = SzResolutionInfo.parseResolutionInfo(null, jsonObject);
        }
      }

      // construct the response
      SzLoadRecordResponse response = new SzLoadRecordResponse(
          PUT, 200, uriInfo, timers, recordId, info);

      // check if we have info and raw data was requested
      if (withRaw && withInfo) {
        response.setRawData(rawInfo);
      }

      // return the response
      return response;

    } catch (ServerErrorException e) {
      e.printStackTrace();
      throw e;

    } catch (WebApplicationException e) {
      throw e;

    } catch (Exception e) {
      e.printStackTrace();
      throw ServicesUtil.newInternalServerErrorException(PUT, uriInfo, timers, e);
    }
  }

  @DELETE
  @Path("data-sources/{dataSourceCode}/records/{recordId}")
  public SzDeleteRecordResponse deleteRecord(
      @PathParam("dataSourceCode")                    String  dataSourceCode,
      @PathParam("recordId")                          String  recordId,
      @QueryParam("loadId")                           String  loadId,
      @QueryParam("withInfo") @DefaultValue("false")  boolean withInfo,
      @QueryParam("withRaw")  @DefaultValue("false")  boolean withRaw,
      @Context                                        UriInfo uriInfo)
  {
    Timers timers = newTimers();
    try {
      SzApiProvider provider = SzApiProvider.Factory.getProvider();
      ensureLoadingIsAllowed(provider, DELETE, uriInfo, timers);
      dataSourceCode = dataSourceCode.trim().toUpperCase();

      final String dataSource = dataSourceCode;

      Set<String> dataSources = provider.getDataSources(dataSource);

      if (!dataSources.contains(dataSource)) {
        throw newNotFoundException(
            DELETE, uriInfo, timers,
            "The specified data source is not recognized: " + dataSource);
      }

      final String normalizedLoadId = normalizeString(loadId);

      // get the asynchronous info sink (if configured)
      boolean asyncInfo = provider.hasInfoSink();

      enteringQueue(timers);
      String rawInfo = provider.executeInThread(() -> {
        exitingQueue(timers);

        // get the engine API
        G2Engine engineApi = provider.getEngineApi();

        int returnCode;
        String rawData = null;
        if (withInfo || asyncInfo) {
          StringBuffer sb = new StringBuffer();
            callingNativeAPI(timers, "engine", "deleteRecordWithInfo");
          returnCode = engineApi.deleteRecordWithInfo(
              dataSource, recordId, normalizedLoadId,0, sb);
          calledNativeAPI(timers, "engine", "deleteRecordWithInfo");
          rawData = sb.toString();
        } else {
          callingNativeAPI(timers, "engine", "deleteRecord");
          returnCode = engineApi.deleteRecord(
              dataSource, recordId, normalizedLoadId);
          calledNativeAPI(timers, "engine", "deleteRecord");
        }
        if (returnCode != 0) {
          int errorCode = engineApi.getLastExceptionCode();
          // if the record was not found, that is okay -- treat as idempotent,
          // but note that "info" will differ when deleting a not-found record
          if (errorCode == RECORD_NOT_FOUND_CODE) {
            return null;
          }
          // otherwise throw a server error
          throw newInternalServerErrorException(
              DELETE, uriInfo, timers, engineApi);
        }

        return rawData;
      });

      SzResolutionInfo info = null;
      if (rawInfo != null && rawInfo.trim().length() > 0) {
        // check if the info sink is configured
        if (asyncInfo) {
          SzMessageSink infoSink = provider.acquireInfoSink();
          SzMessage message = new SzMessage(rawInfo);
          try {
            // send the info on the async queue
            infoSink.send(message, ServicesUtil::logFailedAsyncInfo);

          } catch (Exception e) {
            logFailedAsyncInfo(e, message);

          } finally {
            provider.releaseInfoSink(infoSink);
          }
        }

        // check if the info was explicitly requested
        if (withInfo) {
          JsonObject jsonObject = JsonUtils.parseJsonObject(rawInfo);
          info = SzResolutionInfo.parseResolutionInfo(null, jsonObject);
          if ((normalizeString(info.getDataSource()) == null)
              && (normalizeString(info.getRecordId()) == null)
              && (info.getAffectedEntities().size() == 0)
              && (info.getFlaggedEntities().size() == 0)) {
            info = null;
          }
        }
      }

      // construct the response
      SzDeleteRecordResponse response = new SzDeleteRecordResponse(
          DELETE, 200, uriInfo, timers, info);

      // check if we have info and raw data was requested
      if (withRaw && withInfo) {
        response.setRawData(rawInfo);
      }

      // return the response
      return response;

    } catch (ServerErrorException e) {
      e.printStackTrace();
      throw e;

    } catch (WebApplicationException e) {
      throw e;

    } catch (Exception e) {
      e.printStackTrace();
      throw ServicesUtil.newInternalServerErrorException(
          DELETE, uriInfo, timers, e);
    }
  }

  @POST
  @Path("data-sources/{dataSourceCode}/records/{recordId}/reevaluate")
  public SzReevaluateResponse reevaluateRecord(
      @PathParam("dataSourceCode")                    String  dataSourceCode,
      @PathParam("recordId")                          String  recordId,
      @QueryParam("withInfo") @DefaultValue("false")  boolean withInfo,
      @QueryParam("withRaw")  @DefaultValue("false")  boolean withRaw,
      @Context                                        UriInfo uriInfo)
  {
    Timers timers = newTimers();
    try {
      SzApiProvider provider = SzApiProvider.Factory.getProvider();
      ensureLoadingIsAllowed(provider, POST, uriInfo, timers);
      dataSourceCode = dataSourceCode.trim().toUpperCase();

      final String dataSource = dataSourceCode;

      Set<String> dataSources = provider.getDataSources(dataSource);

      if (!dataSources.contains(dataSource)) {
        throw newNotFoundException(
            POST, uriInfo, timers,
            "The specified data source is not recognized: " + dataSource);
      }

      // get the configured info message sink (if any)
      boolean asyncInfo = provider.hasInfoSink();

      enteringQueue(timers);
      String rawInfo = provider.executeInThread(() -> {
        exitingQueue(timers);

        // get the engine API
        G2Engine engineApi = provider.getEngineApi();

        int returnCode;
        String rawData = null;
        if (withInfo || asyncInfo) {
          StringBuffer sb = new StringBuffer();
          callingNativeAPI(timers, "engine", "reevaluateRecordWithInfo");
          returnCode = engineApi.reevaluateRecordWithInfo(
              dataSource, recordId,0, sb);
          calledNativeAPI(timers, "engine", "reevaluateRecordWithInfo");
          rawData = sb.toString();
        } else {
          callingNativeAPI(timers, "engine", "reevaluateRecord");
          returnCode = engineApi.reevaluateRecord(dataSource, recordId,0);
          calledNativeAPI(timers, "engine", "reevaluateRecord");
        }
        if (returnCode != 0) {
          throw newPossiblyNotFoundException(POST, uriInfo, timers, engineApi);
        }

        return rawData;
      });

      SzResolutionInfo info = null;
      if (rawInfo != null && rawInfo.trim().length() > 0) {
        // check if the info sink is configured
        if (asyncInfo) {
          SzMessageSink infoSink = provider.acquireInfoSink();
          SzMessage message = new SzMessage(rawInfo);
          try {
            infoSink.send(message, ServicesUtil::logFailedAsyncInfo);

          } catch (Exception e) {
            logFailedAsyncInfo(e, message);

          } finally {
            provider.releaseInfoSink(infoSink);
          }
        }

        // check if the info was explicitly requested
        if (withInfo) {
          JsonObject jsonObject = JsonUtils.parseJsonObject(rawInfo);
          info = SzResolutionInfo.parseResolutionInfo(null, jsonObject);
        }
      }

      // construct the response
      SzReevaluateResponse response = new SzReevaluateResponse(
          POST, 200, uriInfo, timers, info);

      // check if we have info and raw data was requested
      if (withRaw && withInfo) {
        response.setRawData(rawInfo);
      }

      // return the response
      return response;

    } catch (ServerErrorException e) {
      e.printStackTrace();
      throw e;

    } catch (WebApplicationException e) {
      throw e;

    } catch (Exception e) {
      e.printStackTrace();
      throw ServicesUtil.newInternalServerErrorException(POST, uriInfo, timers, e);
    }
  }

  @GET
  @Path("data-sources/{dataSourceCode}/records/{recordId}")
  public SzRecordResponse getRecord(
      @PathParam("dataSourceCode")                  String  dataSourceCode,
      @PathParam("recordId")                        String  recordId,
      @DefaultValue("false") @QueryParam("withRaw") boolean withRaw,
      @Context                                      UriInfo uriInfo)
  {
    Timers timers = newTimers();

    try {
      SzApiProvider provider = SzApiProvider.Factory.getProvider();
      dataSourceCode = dataSourceCode.trim().toUpperCase();

      StringBuffer sb = new StringBuffer();

      final String dataSource = dataSourceCode;

      enteringQueue(timers);
      String rawData = provider.executeInThread(() -> {
        exitingQueue(timers);

        // get the engine API
        G2Engine engineApi = provider.getEngineApi();

        callingNativeAPI(timers, "engine", "getRecord");
        int result = engineApi.getRecordV2(
            dataSource, recordId, DEFAULT_RECORD_FLAGS, sb);
        calledNativeAPI(timers, "engine", "getRecord");

        if (result != 0) {
          throw newPossiblyNotFoundException(GET, uriInfo, timers, engineApi);
        }

        return sb.toString();
      });

      processingRawData(timers);

      // parse the raw data
      JsonObject jsonObject = JsonUtils.parseJsonObject(rawData);

      SzEntityRecord entityRecord
          = SzEntityRecord.parseEntityRecord(null, jsonObject);

      processedRawData(timers);

      // construct the response
      SzRecordResponse response = new SzRecordResponse(GET,
                                                       200,
                                                       uriInfo,
                                                       timers,
                                                       entityRecord);

      // if including raw data then add it
      if (withRaw) response.setRawData(rawData);

      // return the response
      return response;

    } catch (ServerErrorException e) {
      e.printStackTrace();
      throw e;

    } catch (WebApplicationException e) {
      throw e;

    } catch (Exception e) {
      e.printStackTrace();
      throw ServicesUtil.newInternalServerErrorException(GET, uriInfo, timers, e);
    }
  }

  @GET
  @Path("data-sources/{dataSourceCode}/records/{recordId}/entity")
  public SzEntityResponse getEntityByRecordId(
      @PathParam("dataSourceCode")                                String              dataSourceCode,
      @PathParam("recordId")                                      String              recordId,
      @DefaultValue("false") @QueryParam("withRaw")               boolean             withRaw,
      @DefaultValue("PARTIAL") @QueryParam("withRelated")         SzRelationshipMode  withRelated,
      @DefaultValue("false") @QueryParam("forceMinimal")          boolean             forceMinimal,
      @DefaultValue("WITH_DUPLICATES") @QueryParam("featureMode") SzFeatureMode       featureMode,
      @DefaultValue("false") @QueryParam("withFeatureStats")      boolean             withFeatureStats,
      @DefaultValue("false") @QueryParam("withInternalFeatures")  boolean             withInternalFeatures,
      @Context                                                    UriInfo             uriInfo)
  {
    Timers timers = newTimers();

    try {
      SzApiProvider provider = SzApiProvider.Factory.getProvider();
      dataSourceCode = dataSourceCode.trim().toUpperCase();

      final String dataSource = dataSourceCode;

      StringBuffer sb = new StringBuffer();

      SzEntityData entityData = null;

      int flags = getFlags(forceMinimal,
                           featureMode,
                           withFeatureStats,
                           withInternalFeatures,
                           (withRelated != SzRelationshipMode.NONE));

      String rawData = null;

      // check if we want 1-degree relations as well -- if so we need to
      // find the network instead of a simple lookup
      if (withRelated == FULL && !forceMinimal) {
        // build the record IDs JSON to find the network
        JsonObjectBuilder builder1 = Json.createObjectBuilder();
        JsonArrayBuilder builder2 = Json.createArrayBuilder();
        JsonObjectBuilder builder3 = Json.createObjectBuilder();
        builder1.add("RECORD_ID", recordId);
        builder1.add("DATA_SOURCE", dataSource);
        builder2.add(builder1);
        builder3.add("RECORDS", builder2);
        String recordIds = JsonUtils.toJsonText(builder3);

        // set the other arguments
        final int maxDegrees = 1;
        final int buildOutDegrees = 1;
        final int maxEntityCount = 1000;

        enteringQueue(timers);
        rawData = provider.executeInThread(() -> {
          exitingQueue(timers);

          // get the engine API and the config API
          G2Engine engineApi = provider.getEngineApi();

          callingNativeAPI(timers, "engine", "findNetworkByRecordIDV2");
          // find the network and check the result
          int result = engineApi.findNetworkByRecordIDV2(
              recordIds, maxDegrees, buildOutDegrees, maxEntityCount, flags, sb);

          calledNativeAPI(timers, "engine", "findNetworkByRecordIDV2");

          if (result != 0) {
            throw newPossiblyNotFoundException(GET, uriInfo, timers, engineApi);
          }

          return sb.toString();
        });

        processingRawData(timers);

        // organize all the entities into a map for lookup
        Map<Long, SzEntityData> dataMap
            = parseEntityDataList(sb.toString(), provider);

        // find the entity ID matching the data source and record ID
        Long entityId = null;
        for (SzEntityData edata : dataMap.values()) {
          SzResolvedEntity resolvedEntity = edata.getResolvedEntity();
          // check if this entity is the one that was requested by record ID
          for (SzMatchedRecord record : resolvedEntity.getRecords()) {
            if (record.getDataSource().equalsIgnoreCase(dataSource)
                && record.getRecordId().equals(recordId)) {
              // found the entity ID for the record ID
              entityId = resolvedEntity.getEntityId();
              break;
            }
          }
          if (entityId != null) break;
        }

        // get the result entity data
        entityData = getAugmentedEntityData(entityId, dataMap, provider);

      } else {
        enteringQueue(timers);
        rawData = provider.executeInThread(() -> {
          exitingQueue(timers);

          // get the engine API and the config API
          G2Engine engineApi = provider.getEngineApi();

          callingNativeAPI(timers, "engine", "getEntityByRecordIDV2");
          // 1-degree relations are not required, so do a standard lookup
          int result = engineApi.getEntityByRecordIDV2(dataSource, recordId, flags, sb);
          calledNativeAPI(timers, "engine", "getEntityByRecordIDV2");

          String engineJSON = sb.toString();
          checkEntityResult(result, engineJSON, uriInfo, timers, engineApi);

          return engineJSON;
        });

        processingRawData(timers);
        // parse the result
        entityData = SzEntityData.parseEntityData(
            null,
            JsonUtils.parseJsonObject(rawData),
            (f) -> provider.getAttributeClassForFeature(f));
      }

      postProcessEntityData(entityData, forceMinimal, featureMode);

      processedRawData(timers);
      // construct the response
      return newEntityResponse(
          uriInfo, timers, entityData, (withRaw ? rawData : null));

    } catch (ServerErrorException e) {
      e.printStackTrace();
      throw e;

    } catch (WebApplicationException e) {
      throw e;

    } catch (Exception e) {
      e.printStackTrace();
      throw ServicesUtil.newInternalServerErrorException(GET, uriInfo, timers, e);
    }
  }

  @GET
  @Path("entities/{entityId}")
  public SzEntityResponse getEntityByEntityId(
      @PathParam("entityId")                                      long                entityId,
      @DefaultValue("false") @QueryParam("withRaw")               boolean             withRaw,
      @DefaultValue("PARTIAL") @QueryParam("withRelated")         SzRelationshipMode  withRelated,
      @DefaultValue("false") @QueryParam("forceMinimal")          boolean             forceMinimal,
      @DefaultValue("WITH_DUPLICATES") @QueryParam("featureMode") SzFeatureMode       featureMode,
      @DefaultValue("false") @QueryParam("withFeatureStats")      boolean             withFeatureStats,
      @DefaultValue("false") @QueryParam("withInternalFeatures")  boolean             withInternalFeatures,
      @Context                                                    UriInfo             uriInfo)
  {
    Timers timers = newTimers();

    try {
      SzApiProvider provider = SzApiProvider.Factory.getProvider();

      StringBuffer sb = new StringBuffer();

      SzEntityData entityData = null;

      String rawData = null;

      int flags = getFlags(forceMinimal,
                           featureMode,
                           withFeatureStats,
                           withInternalFeatures,
                           (withRelated != SzRelationshipMode.NONE));

      // check if we want 1-degree relations as well -- if so we need to
      // find the network instead of a simple lookup
      if (withRelated == FULL && !forceMinimal) {
        // build the entity IDs JSON to find the network
        JsonObjectBuilder builder1 = Json.createObjectBuilder();
        JsonArrayBuilder builder2 = Json.createArrayBuilder();
        JsonObjectBuilder builder3 = Json.createObjectBuilder();
        builder1.add("ENTITY_ID", entityId);
        builder2.add(builder1);
        builder3.add("ENTITIES", builder2);
        String entityIds = JsonUtils.toJsonText(builder3);

        // set the other arguments
        final int maxDegrees = 1;
        final int maxEntityCount = 1000;
        final int buildOutDegrees = 1;

        enteringQueue(timers);
        rawData = provider.executeInThread(() -> {
          exitingQueue(timers);
          // get the engine API
          G2Engine engineApi = provider.getEngineApi();

          callingNativeAPI(timers, "engine", "findNetworkByEntityIDV2");
          // find the network and check the result
          int result = engineApi.findNetworkByEntityIDV2(
              entityIds, maxDegrees, buildOutDegrees, maxEntityCount, flags, sb);

          calledNativeAPI(timers, "engine", "findNetworkByEntityIDV2");

          if (result != 0) {
            throw newPossiblyNotFoundException(GET, uriInfo, timers, engineApi);
          }
          return sb.toString();
        });

        processingRawData(timers);

        // organize all the entities into a map for lookup
        Map<Long, SzEntityData> dataMap
            = parseEntityDataList(rawData, provider);

        // get the result entity data
        entityData = getAugmentedEntityData(entityId, dataMap, provider);

      } else {
        enteringQueue(timers);
        rawData = provider.executeInThread(() -> {
          exitingQueue(timers);

          // get the engine API
          G2Engine engineApi = provider.getEngineApi();

          callingNativeAPI(timers, "engine", "getEntityByEntityIDV2");
          // 1-degree relations are not required, so do a standard lookup
          int result = engineApi.getEntityByEntityIDV2(entityId, flags, sb);
          calledNativeAPI(timers, "engine", "getEntityByEntityIDV2");

          String engineJSON = sb.toString();

          checkEntityResult(result, engineJSON, uriInfo, timers, engineApi);

          return engineJSON;
        });

        processingRawData(timers);

        // parse the result
        entityData = SzEntityData.parseEntityData(
            null,
            JsonUtils.parseJsonObject(rawData),
            (f) -> provider.getAttributeClassForFeature(f));
      }

      postProcessEntityData(entityData, forceMinimal, featureMode);

      processedRawData(timers);

      // construct the response
      return newEntityResponse(
          uriInfo, timers, entityData, (withRaw ? sb.toString() : null));

    } catch (ServerErrorException e) {
      e.printStackTrace();
      throw e;

    } catch (WebApplicationException e) {
      throw e;

    } catch (Exception e) {
      e.printStackTrace();
      throw ServicesUtil.newInternalServerErrorException(GET, uriInfo, timers, e);
    }
  }

  @GET
  @Path("entities")
  public SzAttributeSearchResponse searchEntitiesByGet(
      @QueryParam("attrs")                                        String              attrs,
      @QueryParam("attr")                                         List<String>        attrList,
      @QueryParam("includeOnly")                                  Set<String>         includeOnlySet,
      @DefaultValue("false") @QueryParam("forceMinimal")          boolean             forceMinimal,
      @DefaultValue("WITH_DUPLICATES") @QueryParam("featureMode") SzFeatureMode       featureMode,
      @DefaultValue("false") @QueryParam("withFeatureStats")      boolean             withFeatureStats,
      @DefaultValue("false") @QueryParam("withInternalFeatures")  boolean             withInternalFeatures,
      @DefaultValue("false") @QueryParam("withRelationships")     boolean             withRelationships,
      @DefaultValue("false") @QueryParam("withRaw")               boolean             withRaw,
      @Context                                                    UriInfo             uriInfo)
  {
    Timers timers = newTimers();
    try {
      JsonObject searchCriteria = null;
      if (attrs != null && attrs.trim().length() > 0) {
        try {
          searchCriteria = JsonUtils.parseJsonObject(attrs);
        } catch (Exception e) {
          throw newBadRequestException(
              GET, uriInfo, timers,
              "The search criteria specified via the \"attrs\" parameter "
                  + "does not parse as valid JSON: " + attrs);
        }
      } else if (attrList != null && attrList.size() > 0) {
        Map<String, List<String>> attrMap = new LinkedHashMap<>();
        JsonObjectBuilder objBuilder = Json.createObjectBuilder();
        for (String attrParam : attrList) {
          // check for the colon
          int index = attrParam.indexOf(":");

          // if not found that is a problem
          if (index < 0) {
            throw newBadRequestException(
                GET, uriInfo, timers,
                "The attr param value must be a colon-delimited string, "
                    + "but no colon character was found: " + attrParam);
          }
          if (index == 0) {
            throw newBadRequestException(
                GET, uriInfo, timers,
                "The attr param value must contain a property name followed by "
                    + "a colon, but no property was provided before the colon: "
                    + attrParam);
          }

          // get the property name
          String propName = attrParam.substring(0, index);
          String propValue = "";
          if (index < attrParam.length() - 1) {
            propValue = attrParam.substring(index + 1);
          }

          // store in the map
          List<String> values = attrMap.get(propName);
          if (values == null) {
            values = new LinkedList<>();
            attrMap.put(propName, values);
          }
          values.add(propValue);
        }
        attrMap.entrySet().forEach(entry -> {
          String propName = entry.getKey();
          List<String> propValues = entry.getValue();
          if (propValues.size() == 1) {
            // add the attribute to the object builder
            objBuilder.add(propName, propValues.get(0));
          } else {
            JsonArrayBuilder jab = Json.createArrayBuilder();
            for (String propValue : propValues) {
              JsonObjectBuilder job = Json.createObjectBuilder();
              job.add(propName, propValue);
              jab.add(job);
            }
            objBuilder.add(propName + "_LIST", jab);
          }
        });
        searchCriteria = objBuilder.build();
      }

      // check if we have no attributes at all
      if (searchCriteria == null || searchCriteria.size() == 0) {
        throw newBadRequestException(
            GET, uriInfo, timers,
            "At least one search criteria attribute must be provided via the "
                + "\"attrs\" or \"attr\" parameter.  attrs=[ " + attrs
                + " ], attrList=[ " + attrList + " ]");
      }

      // defer to the internal method
      return this.searchByAttributes(searchCriteria,
                                     includeOnlySet,
                                     forceMinimal,
                                     featureMode,
                                     withFeatureStats,
                                     withInternalFeatures,
                                     withRelationships,
                                     withRaw,
                                     uriInfo,
                                     GET,
                                     timers);

    } catch (ServerErrorException e) {
      e.printStackTrace();
      throw e;

    } catch (WebApplicationException e) {
      throw e;

    } catch (Exception e) {
      e.printStackTrace();
      throw ServicesUtil.newInternalServerErrorException(GET, uriInfo, timers, e);
    }
  }


  @POST
  @Path("search-entities")
  public SzAttributeSearchResponse searchEntitiesByPost(
      @QueryParam("includeOnly")                                  Set<String>     includeOnlySet,
      @DefaultValue("false") @QueryParam("forceMinimal")          boolean         forceMinimal,
      @DefaultValue("WITH_DUPLICATES") @QueryParam("featureMode") SzFeatureMode   featureMode,
      @DefaultValue("false") @QueryParam("withFeatureStats")      boolean         withFeatureStats,
      @DefaultValue("false") @QueryParam("withInternalFeatures")  boolean         withInternalFeatures,
      @DefaultValue("false") @QueryParam("withRelationships")     boolean         withRelationships,
      @DefaultValue("false") @QueryParam("withRaw")               boolean         withRaw,
      @Context                                                    UriInfo         uriInfo,
      String                                                                      attrs)
  {
    Timers timers = newTimers();
    try {
      JsonObject searchCriteria = null;
      if (attrs == null || attrs.trim().length() == 0) {
        throw newBadRequestException(
            POST, uriInfo, timers, "The request body must be provided");
      }
      try {
        searchCriteria = JsonUtils.parseJsonObject(attrs);
      } catch (Exception e) {
        throw newBadRequestException(
            POST, uriInfo, timers,
              "The search criteria in the request body does not parse as "
            + "valid JSON: " + attrs);
      }

      // check if we have no attributes at all
      if (searchCriteria == null || searchCriteria.size() == 0) {
        throw newBadRequestException(
            POST, uriInfo, timers,
            "At least one search criteria attribute must be provided in the "
                + "JSON request body.  requestBody=[ " + attrs + " ]");
      }

      // defer to the internal method
      return this.searchByAttributes(searchCriteria,
                                     includeOnlySet,
                                     forceMinimal,
                                     featureMode,
                                     withFeatureStats,
                                     withInternalFeatures,
                                     withRelationships,
                                     withRaw,
                                     uriInfo,
                                     POST,
                                     timers);

    } catch (ServerErrorException e) {
      e.printStackTrace();
      throw e;

    } catch (WebApplicationException e) {
      throw e;

    } catch (Exception e) {
      e.printStackTrace();
      throw ServicesUtil.newInternalServerErrorException(
          POST, uriInfo, timers, e);
    }
  }

  protected SzAttributeSearchResponse searchByAttributes(
      JsonObject          searchCriteria,
      Set<String>         includeOnlySet,
      boolean             forceMinimal,
      SzFeatureMode       featureMode,
      boolean             withFeatureStats,
      boolean             withInternalFeatures,
      boolean             withRelationships,
      boolean             withRaw,
      UriInfo             uriInfo,
      SzHttpMethod        httpMethod,
      Timers              timers)
  {
    try {
      SzApiProvider provider = SzApiProvider.Factory.getProvider();

      // check for the include-only parameters, convert to result types
      if (includeOnlySet == null) includeOnlySet = Collections.emptySet();
      List<SzAttributeSearchResultType> resultTypes
          = new ArrayList<>(includeOnlySet.size());
      for (String includeOnly : includeOnlySet) {
        try {
          resultTypes.add(SzAttributeSearchResultType.valueOf(includeOnly));

        } catch (Exception e) {
          throw newBadRequestException(
              httpMethod, uriInfo, timers,
              "At least one of the includeOnly parameter values was not "
              + "recognized: " + includeOnly);
        }
      }

      // augment the flags based on includeOnly parameter result types
      int includeFlags = 0;
      SemanticVersion version
          = new SemanticVersion(provider.getNativeApiVersion());

      boolean supportFiltering
          = MINIMUM_SEARCH_FILTERING_VERSION.compareTo(version) <= 0;

      // only support the include flags on versions where it works
      if (supportFiltering) {
        for (SzAttributeSearchResultType resultType : resultTypes) {
          Integer flag = RESULT_TYPE_FLAG_MAP.get(resultType);
          if (flag == null) continue;
          includeFlags |= flag.intValue();
        }
      }

      // create the response buffer
      StringBuffer sb = new StringBuffer();

      // get the flags
      int flags = getFlags(includeFlags,
                           forceMinimal,
                           featureMode,
                           withFeatureStats,
                           withInternalFeatures,
                           withRelationships);

      // format the search JSON
      final String searchJson = JsonUtils.toJsonText(searchCriteria);

      enteringQueue(timers);
      provider.executeInThread(() -> {
        exitingQueue(timers);

        // get the engine API
        G2Engine engineApi = provider.getEngineApi();

        callingNativeAPI(timers, "engine", "searchByAttributesV2");
        int result = engineApi.searchByAttributesV2(searchJson, flags, sb);
        calledNativeAPI(timers, "engine", "searchByAttributesV2");
        if (result != 0) {
          throw newInternalServerErrorException(
              httpMethod, uriInfo, timers, engineApi);
        }
        return sb.toString();
      });

      processingRawData(timers);

      JsonObject jsonObject = JsonUtils.parseJsonObject(sb.toString());
      JsonArray jsonResults = jsonObject.getValue(
          "/RESOLVED_ENTITIES").asJsonArray();

      // parse the result
      List<SzAttributeSearchResult> list
          = SzAttributeSearchResult.parseSearchResultList(
          null,
          jsonResults,
          (f) -> provider.getAttributeClassForFeature(f));


      postProcessSearchResults(
          list, forceMinimal, featureMode, withRelationships);

      // construct the response
      SzAttributeSearchResponse response
          = new SzAttributeSearchResponse(
              httpMethod, 200, uriInfo, timers);

      response.setSearchResults(list);

      if (withRaw) {
        response.setRawData(sb.toString());
      }

      processedRawData(timers);

      // return the response
      return response;

    } catch (ServerErrorException e) {
      e.printStackTrace();
      throw e;

    } catch (WebApplicationException e) {
      throw e;

    } catch (Exception e) {
      e.printStackTrace();
      throw ServicesUtil.newInternalServerErrorException(
          httpMethod, uriInfo, timers, e);
    }
  }

  @POST
  @Path("reevaluate-entity")
  public SzReevaluateResponse reevaluateEntity(
      @QueryParam("entityId")                         Long    entityId,
      @QueryParam("withInfo") @DefaultValue("false")  boolean withInfo,
      @QueryParam("withRaw")  @DefaultValue("false")  boolean withRaw,
      @Context                                        UriInfo uriInfo)
  {
    Timers timers = newTimers();
    try {
      SzApiProvider provider = SzApiProvider.Factory.getProvider();
      ensureLoadingIsAllowed(provider, POST, uriInfo, timers);

      if (entityId == null) {
        throw newBadRequestException(
            POST, uriInfo, timers, "The entityId parameter is required.");
      }

      // get the info sink (if configured)
      boolean asyncInfo = provider.hasInfoSink();

      enteringQueue(timers);
      String rawInfo = provider.executeInThread(() -> {
        exitingQueue(timers);

        // get the engine API
        G2Engine      engineApi = provider.getEngineApi();

        int returnCode;
        String rawData = null;
        if (withInfo || asyncInfo) {
          StringBuffer sb = new StringBuffer();
          callingNativeAPI(timers, "engine", "reevaluateEntityWithInfo");
          returnCode = engineApi.reevaluateEntityWithInfo(entityId,0, sb);
          calledNativeAPI(timers, "engine", "reevaluateEntityWithInfo");
          rawData = sb.toString();
        } else {
          callingNativeAPI(timers, "engine", "reevaluateEntity");
          returnCode = engineApi.reevaluateEntity(entityId,0);
          calledNativeAPI(timers, "engine", "reevaluateEntity");
        }

        if (returnCode != 0) {
          int errorCode = engineApi.getLastExceptionCode();
          if (errorCode == ENTITY_ID_NOT_FOUND_CODE) {
            throw newBadRequestException(
                POST, uriInfo, timers, "The specified entityId was not found: "
                    + entityId);
          } else {
            throw newPossiblyNotFoundException(POST, uriInfo, timers, engineApi);
          }
        }

        return rawData;
      });

      SzResolutionInfo info = null;
      if (rawInfo != null && rawInfo.trim().length() > 0) {
        // check if the info sink is configured
        if (asyncInfo) {
          SzMessageSink infoSink = provider.acquireInfoSink();
          SzMessage message = new SzMessage(rawInfo);
          try {
            // send the info on the async queue
            infoSink.send(message, ServicesUtil::logFailedAsyncInfo);

          } catch (Exception e) {
            logFailedAsyncInfo(e, message);

          } finally {
            provider.releaseInfoSink(infoSink);
          }
        }

        // if the info was requested, then we also want to parse and return it
        if (withInfo) {
          info = SzResolutionInfo.parseResolutionInfo(
              null, JsonUtils.parseJsonObject(rawInfo));
        }
      }

      // construct the response
      SzReevaluateResponse response = new SzReevaluateResponse(
          POST, 200, uriInfo, timers, info);

      // check if we have info and raw data was requested
      if (withRaw && withInfo) {
        response.setRawData(rawInfo);
      }

      // return the response
      return response;

    } catch (ServerErrorException e) {
      e.printStackTrace();
      throw e;

    } catch (WebApplicationException e) {
      throw e;

    } catch (Exception e) {
      e.printStackTrace();
      throw ServicesUtil.newInternalServerErrorException(POST, uriInfo, timers, e);
    }
  }

  /**
   *
   * @param entityId
   * @param dataMap
   * @param provider
   * @return
   */
  private static SzEntityData getAugmentedEntityData(
      long                      entityId,
      Map<Long, SzEntityData>   dataMap,
      SzApiProvider             provider)
  {
    // get the result entity data
    SzEntityData entityData = dataMap.get(entityId);

    // check if we can augment the related entities that were found
    // so they are not partial responses since they are part of the
    // entity network build-out
    List<SzRelatedEntity> relatedEntities
        = entityData.getRelatedEntities();

    // loop over the related entities
    for (SzRelatedEntity relatedEntity : relatedEntities) {
      // get the related entity data (should be present)
      SzEntityData relatedData = dataMap.get(relatedEntity.getEntityId());

      // just in case not present because of entity count limits
      if (relatedData == null) continue;

      // get the resolved entity for the related entity
      SzResolvedEntity related = relatedData.getResolvedEntity();

      // get the features and records
      Map<String, List<SzEntityFeature>> features
          = related.getFeatures();

      List<SzMatchedRecord> records = related.getRecords();

      // summarize the records
      List<SzDataSourceRecordSummary> summaries
          = SzResolvedEntity.summarizeRecords(records);

      // set the features and "data" fields
      relatedEntity.setFeatures(
          features, (f) -> provider.getAttributeClassForFeature(f));

      // set the records and record summaries
      relatedEntity.setRecords(records);
      relatedEntity.setRecordSummaries(summaries);
      relatedEntity.setPartial(false);
    }

    return entityData;
  }

  /**
   *
   */
  private static Map<Long, SzEntityData> parseEntityDataList(
      String rawData, SzApiProvider provider)
  {
    // parse the raw response and extract the entities that were found
    JsonObject jsonObj = JsonUtils.parseJsonObject(rawData);
    JsonArray jsonArr = jsonObj.getJsonArray("ENTITIES");

    List<SzEntityData> list = SzEntityData.parseEntityDataList(
        null, jsonArr, (f) -> provider.getAttributeClassForFeature(f));

    // organize all the entities into a map for lookup
    Map<Long, SzEntityData> dataMap = new LinkedHashMap<>();
    for (SzEntityData edata : list) {
      SzResolvedEntity resolvedEntity = edata.getResolvedEntity();
      dataMap.put(resolvedEntity.getEntityId(), edata);
    }

    return dataMap;
  }

  /**
   *
   */
  private static SzEntityResponse newEntityResponse(UriInfo       uriInfo,
                                                    Timers        timers,
                                                    SzEntityData  entityData,
                                                    String        rawData)
  {
    // construct the response
    SzEntityResponse response = new SzEntityResponse(GET,
                                                     200,
                                                     uriInfo,
                                                     timers,
                                                     entityData);

    // if including raw data then add it
    if (rawData != null) response.setRawData(rawData);

    // return the response
    return response;

  }

  /**
   *
   */
  private static void checkEntityResult(int       result,
                                        String    nativeJson,
                                        UriInfo   uriInfo,
                                        Timers    timers,
                                        G2Engine  engineApi)
  {
    // check if failed to find result
    if (result != 0) {
      throw newPossiblyNotFoundException(GET, uriInfo, timers, engineApi);
    }
    if (nativeJson.trim().length() == 0) {
      throw newNotFoundException(GET, uriInfo, timers);
    }
  }

  /**
   *
   */
  private static String normalizeString(String text) {
    if (text == null) return null;
    if (text.trim().length() == 0) return null;
    return text.trim();
  }

  /**
   * Ensures the specified data source exists for the provider and thows a
   * NotFoundException if not.
   *
   * @throws NotFoundException If the data source is not found.
   */
  private static void checkDataSource(SzHttpMethod  httpMethod,
                                      UriInfo       uriInfo,
                                      Timers        timers,
                                      String        dataSource,
                                      SzApiProvider apiProvider)
    throws NotFoundException
  {
    Set<String> dataSources = apiProvider.getDataSources(dataSource);

    if (!dataSources.contains(dataSource)) {
      throw newNotFoundException(
          POST, uriInfo, timers,
          "The specified data source is not recognized: " + dataSource);
    }
  }

  /**
   * Ensures the JSON fields in the map are in the specified JSON text.
   * This is a utility method.
   */
  private static String ensureJsonFields(SzHttpMethod         httpMethod,
                                         UriInfo              uriInfo,
                                         Timers               timers,
                                         String               jsonText,
                                         Map<String, String>  map,
                                         Map<String, String>  defaultMap)
  {
    try {
      JsonObject jsonObject = JsonUtils.parseJsonObject(jsonText);
      JsonObjectBuilder jsonBuilder = Json.createObjectBuilder(jsonObject);

      map.entrySet().forEach(entry -> {
        String key = entry.getKey();
        String val = entry.getValue();

        String jsonVal = JsonUtils.getString(jsonObject, key.toUpperCase());
        if (jsonVal == null) {
          jsonVal = JsonUtils.getString(jsonObject, key.toLowerCase());
        }
        if (jsonVal != null && jsonVal.trim().length() > 0) {
          if (!jsonVal.equalsIgnoreCase(val)) {
            throw ServicesUtil.newBadRequestException(
                httpMethod, uriInfo, timers,
                key + " from path and from request body do not match.  "
                    + "fromPath=[ " + val + " ], fromRequestBody=[ "
                    + jsonVal + " ]");
          }
        } else {
          // we need to add the value for the key
          jsonBuilder.add(key, val);
        }
      });

      // iterate over the default values
      defaultMap.forEach((key, val) -> {
        // get the value for the key
        String jsonVal = JsonUtils.getString(jsonObject, key.toUpperCase());
        if (jsonVal == null) {
          jsonVal = JsonUtils.getString(jsonObject, key.toLowerCase());
          if (jsonVal != null) key = key.toLowerCase();
        }
        if (jsonVal == null || jsonVal.trim().length() == 0) {
          if (jsonObject.containsKey(key)) jsonBuilder.remove(key);
          jsonBuilder.add(key, val);
        }
      });

      return JsonUtils.toJsonText(jsonBuilder);

    } catch (ServerErrorException e) {
      e.printStackTrace();
      throw e;

    } catch (WebApplicationException e) {
      throw e;

    } catch (Exception e) {
      throw ServicesUtil.newBadRequestException(httpMethod,
                                                uriInfo,
                                                timers,
                                                e.getMessage());
    }
  }

  /**
   * Post-processes the search results according to the specified parameters.
   *
   * @param searchResults The {@link List} of {@link SzAttributeSearchResult}
   *                      to modify.
   *
   * @param forceMinimal Whether or not minimal format is forced.
   *
   * @param featureMode The {@link SzFeatureMode} describing how features
   *                    are retrieved.
   *
   * @param withRelationships Whether or not to include relationships.
   */
  private static void postProcessSearchResults(
      List<SzAttributeSearchResult>   searchResults,
      boolean                         forceMinimal,
      SzFeatureMode                   featureMode,
      boolean                         withRelationships)
  {
    // check if we need to strip out duplicate features
    if (featureMode == REPRESENTATIVE) {
      stripDuplicateFeatureValues(searchResults);
    }

    // check if fields are going to be null if they would otherwise be set
    if (featureMode == SzFeatureMode.NONE || forceMinimal) {
      setEntitiesPartial(searchResults);
    }
  }

  /**
   * Sets the partial flags for the resolved entity and related
   * entities in the {@link SzEntityData}.
   */
  private static void setEntitiesPartial(
      List<SzAttributeSearchResult> searchResults)
  {
    searchResults.forEach(e -> {
      e.setPartial(true);

      e.getRelatedEntities().forEach(e2 -> {
        e2.setPartial(true);
      });
    });
  }

  /**
   * Strips out duplicate feature values for each feature in the search
   * result entities of the specified {@link List} of {@link
   * SzAttributeSearchResult} instances.
   */
  private static void stripDuplicateFeatureValues(
      List<SzAttributeSearchResult> searchResults)
  {
    searchResults.forEach(e -> {
      ServicesUtil.stripDuplicateFeatureValues(e);

      e.getRelatedEntities().forEach(e2 -> {
        ServicesUtil.stripDuplicateFeatureValues(e2);
      });
    });
  }
}
