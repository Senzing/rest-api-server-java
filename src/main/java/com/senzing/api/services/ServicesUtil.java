package com.senzing.api.services;

import com.senzing.api.model.*;
import com.senzing.g2.engine.G2ConfigMgr;
import com.senzing.g2.engine.G2Engine;
import com.senzing.g2.engine.G2Fallible;
import com.senzing.util.ErrorLogSuppressor;
import com.senzing.util.JsonUtils;
import com.senzing.util.Timers;

import javax.json.*;
import javax.servlet.http.HttpServletRequest;
import javax.websocket.Session;
import javax.ws.rs.*;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URLEncoder;
import java.util.*;

import static com.senzing.api.model.SzFeatureMode.*;
import static com.senzing.g2.engine.G2Engine.*;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
/**
 * Utility functions for services.
 */
public class ServicesUtil {
  /**
   * The suppression state for info errors.
   */
  private static final ErrorLogSuppressor INFO_ERROR_SUPPRESSOR
      = new ErrorLogSuppressor();

  /**
   * The hash of the system identity codes of the last info message exception
   * as well as the the message object that was sent.
   */
  private static final ThreadLocal<Long> LAST_INFO_ERROR_HASH
      = new ThreadLocal<>();

  /**
   * HTTP Response code for server error.
   */
  public static final int SERVER_ERROR = 500;

  /**
   * HTTP Response code for service unavailable error.
   */
  public static final int SERVICE_UNAVAILABLE = 503;

  /**
   * HTTP Response code for bad request.
   */
  public static final int BAD_REQUEST = 400;

  /**
   * HTTP Response code for forbidden.
   */
  public static final int FORBIDDEN = 403;

  /**
   * HTTP Response code for "not found".
   * \
   */
  public static final int NOT_FOUND = 404;

  /**
   * HTTP Response code for "not allowed".
   */
  public static final int NOT_ALLOWED = 405;

  /**
   * Error code when a data source code is not found.
   */
  static final int DATA_SOURCE_NOT_FOUND_CODE = 27;

  /**
   * Error code when a record ID is not found.
   */
  static final int RECORD_NOT_FOUND_CODE = 33;

  /**
   * Error code when the entity ID is not found.
   */
  static final int ENTITY_ID_NOT_FOUND_CODE = 37;

  /**
   * Default flags for retrieving records.
   */
  public static final int DEFAULT_RECORD_FLAGS
      = G2_ENTITY_INCLUDE_RECORD_FORMATTED_DATA
      | G2_ENTITY_INCLUDE_RECORD_MATCHING_INFO
      | G2_ENTITY_INCLUDE_RECORD_JSON_DATA
      | G2_ENTITY_INCLUDE_RECORD_SUMMARY;

  /**
   * Creates an {@link InternalServerErrorException} and builds a response
   * with an {@link SzErrorResponse} using the specified {@link UriInfo}
   * and the specified exception.
   *
   * @param httpMethod The HTTP method for the request.
   * @param uriInfo    The {@link UriInfo} from the request.
   * @param timers     The {@link Timers} object for the timings that were taken.
   * @param exception  The exception that caused the error.
   * @return The {@link InternalServerErrorException}
   */
  static InternalServerErrorException newInternalServerErrorException(
      SzHttpMethod httpMethod,
      UriInfo uriInfo,
      Timers timers,
      Exception exception) {
    Response.ResponseBuilder builder = Response.status(SERVER_ERROR);
    builder.entity(
        new SzErrorResponse(httpMethod, SERVER_ERROR, uriInfo, timers, exception));
    builder.type(APPLICATION_JSON);
    return new InternalServerErrorException(builder.build());
  }

  /**
   * Creates an {@link ServiceUnavailableException} and builds a response
   * with an {@link SzErrorResponse} using the specified {@link UriInfo}
   * and the specified exception.
   *
   * @param httpMethod The HTTP method for the request.
   * @param uriInfo    The {@link UriInfo} from the request.
   * @param timers     The {@link Timers} object for the timings that were taken.
   * @param message    The message describing the error.
   * @return The {@link ServiceUnavailableException}
   */
  static ServiceUnavailableException newServiceUnavailableErrorException(
      SzHttpMethod  httpMethod,
      UriInfo       uriInfo,
      Timers        timers,
      String        message)
  {
    Response.ResponseBuilder builder = Response.status(SERVICE_UNAVAILABLE);

    builder.entity(
        new SzErrorResponse(
            httpMethod, SERVICE_UNAVAILABLE, uriInfo, timers, message));
    builder.type(APPLICATION_JSON);
    return new ServiceUnavailableException(builder.build());
  }

  /**
   * Creates an {@link InternalServerErrorException} and builds a response
   * with an {@link SzErrorResponse} using the specified  {@link UriInfo}
   * and the specified {@link G2Fallible} to get the last exception from.
   *
   * @param httpMethod The HTTP method for the request.
   * @param uriInfo    The {@link UriInfo} from the request.
   * @param timers     The {@link Timers} object for the timings that were taken.
   * @param fallible   The {@link G2Fallible} to get the last exception from.
   * @return The {@link InternalServerErrorException}
   */
  static InternalServerErrorException newInternalServerErrorException(
      SzHttpMethod httpMethod,
      UriInfo uriInfo,
      Timers timers,
      G2Fallible fallible) {
    Response.ResponseBuilder builder = Response.status(SERVER_ERROR);
    SzErrorResponse errorResponse =
        new SzErrorResponse(httpMethod, SERVER_ERROR, uriInfo, timers, fallible);
    builder.entity(errorResponse);
    builder.type(APPLICATION_JSON);
    fallible.clearLastException();
    return new InternalServerErrorException(
        errorResponse.getErrors().toString(),
        builder.build());
  }

  /**
   * Creates an {@link NotFoundException} and builds a response
   * with an {@link SzErrorResponse} using the specified {@link UriInfo}
   * and the specified {@link G2Fallible} to get the last exception from.
   *
   * @param httpMethod The HTTP method for the request.
   * @param uriInfo    The {@link UriInfo} from the request.
   * @param timers     The {@link Timers} object for the timings that were taken.
   * @param fallible   The {@link G2Fallible} to get the last exception from.
   * @return The {@link InternalServerErrorException}
   */
  static NotFoundException newNotFoundException(
      SzHttpMethod httpMethod,
      UriInfo uriInfo,
      Timers timers,
      G2Fallible fallible) {
    Response.ResponseBuilder builder = Response.status(NOT_FOUND);
    builder.entity(
        new SzErrorResponse(httpMethod, NOT_FOUND, uriInfo, timers, fallible));
    builder.type(APPLICATION_JSON);
    fallible.clearLastException();
    return new NotFoundException(builder.build());
  }

  /**
   * Creates an {@link NotFoundException} and builds a response
   * with an {@link SzErrorResponse} using the specified {@link UriInfo}.
   *
   * @param httpMethod The HTTP method for the request.
   * @param uriInfo    The {@link UriInfo} from the request.
   * @param timers     The {@link Timers} object for the timings that were taken.
   * @return The {@link InternalServerErrorException}
   */
  static NotFoundException newNotFoundException(
      SzHttpMethod httpMethod,
      UriInfo uriInfo,
      Timers timers) {
    Response.ResponseBuilder builder = Response.status(NOT_FOUND);
    builder.entity(
        new SzErrorResponse(httpMethod, NOT_FOUND, uriInfo, timers));
    builder.type(APPLICATION_JSON);
    return new NotFoundException(builder.build());
  }

  /**
   * Creates an {@link NotFoundException} and builds a response
   * with an {@link SzErrorResponse} using the specified {@link UriInfo}.
   *
   * @param httpMethod   The HTTP method for the request.
   * @param uriInfo      The {@link UriInfo} from the request.
   * @param timers       The {@link Timers} object for the timings that were taken.
   * @param errorMessage The error message.
   * @return The {@link InternalServerErrorException}
   */
  static NotFoundException newNotFoundException(
      SzHttpMethod httpMethod,
      UriInfo uriInfo,
      Timers timers,
      String errorMessage) {
    Response.ResponseBuilder builder = Response.status(NOT_FOUND);
    builder.entity(
        new SzErrorResponse(
            httpMethod, NOT_FOUND, uriInfo, timers, errorMessage));
    builder.type(APPLICATION_JSON);
    return new NotFoundException(builder.build());
  }

  /**
   * Creates a {@link NotAllowedException} and builds a response
   * with an {@link SzErrorResponse} using the specified {@link UriInfo}.
   *
   * @param httpMethod   The HTTP method for the request.
   * @param uriInfo      The {@link UriInfo} from the request.
   * @param timers       The {@link Timers} object for the timings that were taken.
   * @param errorMessage The error message.
   * @return The {@link InternalServerErrorException}
   */
  static NotAllowedException newNotAllowedException(
      SzHttpMethod httpMethod,
      UriInfo uriInfo,
      Timers timers,
      String errorMessage) {
    Response.ResponseBuilder builder = Response.status(NOT_ALLOWED);
    builder.entity(
        new SzErrorResponse(
            httpMethod, NOT_ALLOWED, uriInfo, timers, errorMessage));
    builder.type(APPLICATION_JSON);
    return new NotAllowedException(builder.build());
  }

  /**
   * Creates an {@link BadRequestException} and builds a response
   * with an {@link SzErrorResponse} using the specified {@link UriInfo}
   * and the specified {@link G2Fallible} to get the last exception from.
   *
   * @param httpMethod The HTTP method for the request.
   * @param uriInfo    The {@link UriInfo} from the request.
   * @param timers     The {@link Timers} object for the timings that were taken.
   * @param fallible   The {@link G2Fallible} to get the last exception from.
   * @return The {@link BadRequestException}
   */
  static BadRequestException newBadRequestException(
      SzHttpMethod httpMethod,
      UriInfo uriInfo,
      Timers timers,
      G2Fallible fallible) {
    Response.ResponseBuilder builder = Response.status(BAD_REQUEST);
    builder.entity(
        new SzErrorResponse(httpMethod, BAD_REQUEST, uriInfo, timers, fallible));
    builder.type(APPLICATION_JSON);
    fallible.clearLastException();
    return new BadRequestException(builder.build());
  }

  /**
   * Creates an {@link BadRequestException} and builds a response
   * with an {@link SzErrorResponse} using the specified {@link UriInfo}.
   *
   * @param httpMethod   The HTTP method for the request.
   * @param uriInfo      The {@link UriInfo} from the request.
   * @param timers       The {@link Timers} object for the timings that were taken.
   * @param errorMessage The error message.
   * @return The {@link BadRequestException} that was created with the
   * specified http method and {@link UriInfo}.
   */
  static BadRequestException newBadRequestException(
      SzHttpMethod httpMethod,
      UriInfo uriInfo,
      Timers timers,
      String errorMessage) {
    Response.ResponseBuilder builder = Response.status(BAD_REQUEST);
    builder.entity(
        new SzErrorResponse(
            httpMethod, BAD_REQUEST, uriInfo, timers, errorMessage));
    builder.type(APPLICATION_JSON);
    return new BadRequestException(builder.build());
  }

  /**
   * Creates an {@link InternalServerErrorException} and builds a response
   * with an {@link SzErrorResponse} using the specified {@link UriInfo}
   * and the specified exception.
   *
   * @param httpMethod The HTTP method for the request.
   * @param uriInfo    The {@link UriInfo} from the request.
   * @param timers     The {@link Timers} object for the timings that were taken.
   * @param exception  The exception that caused the error.
   * @return The {@link InternalServerErrorException}
   */
  static BadRequestException newBadRequestException(
      SzHttpMethod httpMethod,
      UriInfo uriInfo,
      Timers timers,
      Exception exception) {
    Response.ResponseBuilder builder = Response.status(BAD_REQUEST);
    builder.entity(
        new SzErrorResponse(httpMethod, BAD_REQUEST, uriInfo, timers, exception));
    builder.type(APPLICATION_JSON);
    return new BadRequestException(builder.build());
  }

  /**
   * Creates an {@link ForbiddenException} and builds a response
   * with an {@link SzErrorResponse} using the specified {@link UriInfo}.
   *
   * @param httpMethod   The HTTP method for the request.
   * @param uriInfo      The {@link UriInfo} from the request.
   * @param timers       The {@link Timers} object for the timings that were taken.
   * @param errorMessage The error message.
   * @return The {@link ForbiddenException} that was created with the
   * specified http method and {@link UriInfo}.
   */
  static ForbiddenException newForbiddenException(
      SzHttpMethod httpMethod,
      UriInfo uriInfo,
      Timers timers,
      String errorMessage) {
    Response.ResponseBuilder builder = Response.status(FORBIDDEN);
    builder.header("Content-Type", APPLICATION_JSON);
    builder.entity(
        new SzErrorResponse(
            httpMethod, FORBIDDEN, uriInfo, timers, errorMessage));
    builder.type(APPLICATION_JSON);
    return new ForbiddenException(builder.build());
  }

  /**
   * When an Engine API operation fails this will either throw a
   * {@link NotFoundException} if the error code indicates a data source,
   * record ID or entity ID was not found, <b>or</b> it will throw an
   * {@link InternalServerErrorException}.
   *
   * @param httpMethod The HTTP method for the request.
   * @param uriInfo    The {@link UriInfo} from the request.
   * @param timers     The {@link Timers} object for the timings that were taken.
   * @param engineApi  The {@link G2Fallible} to get the last exception from.
   * @return A newly created {@link NotFoundException} or {@link
   * InternalServerErrorException}.
   */
  static WebApplicationException newPossiblyNotFoundException(
      SzHttpMethod httpMethod,
      UriInfo uriInfo,
      Timers timers,
      G2Engine engineApi) {
    int errorCode = engineApi.getLastExceptionCode();
    if (errorCode == DATA_SOURCE_NOT_FOUND_CODE
        || errorCode == RECORD_NOT_FOUND_CODE
        || errorCode == ENTITY_ID_NOT_FOUND_CODE) {
      return newNotFoundException(httpMethod, uriInfo, timers, engineApi);
    }
    return newInternalServerErrorException(
        httpMethod, uriInfo, timers, engineApi);
  }

  /**
   * When an Engine API operation fails this will either throw a
   * {@link BadRequestException} if the error code indicates a data source,
   * record ID or entity ID was not found, <b>or</b> it will throw an
   * {@link InternalServerErrorException}.
   *
   * @param httpMethod The HTTP method for the request.
   * @param uriInfo    The {@link UriInfo} from the request.
   * @param timers     The {@link Timers} object for the timings that were taken.
   * @param engineApi  The {@link G2Fallible} to get the last exception from.
   * @return A newly created {@link BadRequestException} or {@link
   * InternalServerErrorException}.
   */
  static WebApplicationException newPossiblyBadRequestException(
      SzHttpMethod httpMethod,
      UriInfo uriInfo,
      Timers timers,
      G2Engine engineApi) {
    int errorCode = engineApi.getLastExceptionCode();
    if (errorCode == DATA_SOURCE_NOT_FOUND_CODE
        || errorCode == RECORD_NOT_FOUND_CODE
        || errorCode == ENTITY_ID_NOT_FOUND_CODE) {
      return newBadRequestException(httpMethod, uriInfo, timers, engineApi);
    }
    return newInternalServerErrorException(
        httpMethod, uriInfo, timers, engineApi);
  }

  /**
   * URL encodes the specified text using UTF-8 encoding.
   *
   * @param text The text to encode.
   * @return The URL-encoded text.
   */
  static String urlEncode(String text) {
    try {
      return URLEncoder.encode(text, "UTF-8");

    } catch (UnsupportedEncodingException cannotHappen) {
      throw new RuntimeException(cannotHappen);
    }
  }

  /**
   * Encodes a {@Link List} of {@link String} instances as a JSON array
   * string (without the leading and terminating brackets) with each of
   * the individual items being URL encoded.
   *
   * @param list The {@link List} of strings to encode.
   * @return The encoded string.
   */
  static String urlEncodeStrings(List<String> list) {
    StringBuilder sb = new StringBuilder();
    String prefix = "";
    for (String item : list) {
      sb.append(prefix);
      prefix = ",";
      try {
        sb.append(URLEncoder.encode(item, "UTF-8"));
      } catch (UnsupportedEncodingException cannotHappen) {
        // do nothing
      }
    }
    return sb.toString();
  }

  /**
   * Encodes a {@Link List} of {@link String} instances as a JSON array
   * string.
   *
   * @param list The {@link List} of strings to encode.
   * @return The encoded string.
   */
  static String jsonEncodeStrings(List<String> list) {
    JsonArrayBuilder jab = Json.createArrayBuilder();
    for (String item : list) {
      jab.add(item);
    }
    return JsonUtils.toJsonText(jab);
  }

  /**
   * Encodes a {@link List} of {@link SzEntityIdentifier} instances as a
   * JSON array string.
   *
   * @param list The list of entity identifiers.
   * @return The JSON array string for the identifiers.
   */
  static String jsonEncodeEntityIds(List<SzEntityIdentifier> list) {
    JsonArrayBuilder jab = Json.createArrayBuilder();
    for (SzEntityIdentifier id : list) {
      if (id instanceof SzEntityId) {
        jab.add(((SzEntityId) id).getValue());
      } else {
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("src", ((SzRecordId) id).getDataSourceCode());
        job.add("id", ((SzRecordId) id).getRecordId());
        jab.add(job);
      }
    }
    return JsonUtils.toJsonText(jab);
  }

  /**
   * Encodes a {@link Collection} of {@link SzEntityIdentifier} instances as a
   * JSON array string in the native Senzing format format for entity
   * identifiers.
   *
   * @param ids The {@link Collection} of entity identifiers.
   * @return The JSON array string for the identifiers.
   */
  public static String nativeJsonEncodeEntityIds(
      Collection<SzEntityIdentifier> ids) {
    if (ids == null) {
      return null;
    }
    String propName = "ENTITIES";
    JsonArrayBuilder jab = Json.createArrayBuilder();
    for (SzEntityIdentifier id : ids) {
      if (id instanceof SzEntityId) {
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("ENTITY_ID", ((SzEntityId) id).getValue());
        jab.add(job);

      } else {
        propName = "RECORDS";
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("DATA_SOURCE", ((SzRecordId) id).getDataSourceCode());
        job.add("RECORD_ID", ((SzRecordId) id).getRecordId());
        jab.add(job);
      }
    }
    JsonObjectBuilder builder = Json.createObjectBuilder();
    builder.add(propName, jab);
    return JsonUtils.toJsonText(builder);
  }

  /**
   * Encodes a {@link Collection} of {@link SzEntityIdentifier} instances as a
   * JSON array string (without the leading and terminating brackets)
   * with each of the individual items in the list being URL encoded.
   *
   * @param ids The {@link Collection} of entity identifiers.
   * @return The URL-encoded JSON array string for the identifiers.
   */
  public static String urlEncodeEntityIds(Collection<SzEntityIdentifier> ids) {
    StringBuilder sb = new StringBuilder();
    String prefix = "";
    for (SzEntityIdentifier id : ids) {
      sb.append(prefix);
      prefix = ",";
      if (id instanceof SzEntityId) {
        sb.append(((SzEntityId) id).getValue());
      } else {
        try {
          sb.append(URLEncoder.encode(id.toString(), "UTF-8"));
        } catch (UnsupportedEncodingException cannotHappen) {
          // do nothing
        }
      }
    }
    return sb.toString();
  }

  /**
   * Encodes a {@link List} of {@link String} data source codes in the
   * Senzing native JSON format for specifying data sources.
   *
   * @param list The list of entity identifiers.
   * @return The JSON array string for the identifiers.
   */
  public static String nativeJsonEncodeDataSources(List<String> list) {
    JsonArrayBuilder jab = Json.createArrayBuilder();
    for (String code : list) {
      jab.add(code);
    }
    JsonObjectBuilder builder = Json.createObjectBuilder();
    builder.add("DATA_SOURCES", jab);
    return JsonUtils.toJsonText(builder);
  }

  /**
   * Formats multiple parameter values into a URL-encoded string of
   * multi-valued URL parameters.
   *
   * @param prefix    The prefix to use (either "&" or "?").
   * @param paramName The parameter name.
   * @param values    The {@link List} of values.
   * @return The multi-valued query string parameter.
   */
  static String formatMultiValuedParam(String prefix,
                                       String paramName,
                                       List<String> values) {
    if (values == null || values.size() == 0) return "";
    StringBuilder sb = new StringBuilder();
    for (String val : values) {
      sb.append(prefix);
      sb.append(paramName);
      sb.append("=");
      sb.append(urlEncode(val));
      prefix = "&";
    }
    return sb.toString();
  }

  /**
   * Combines multiple {@link String} entity identifier parameters into a
   * JSON array and parses it as JSON to produce a {@link Set} of {@link
   * SzEntityIdentifier} instances.
   *
   * @param params     The paramter values to parse.
   * @param paramName  The name of the parameter.
   * @param httpMethod The HTTP method.
   * @param uriInfo    The {@link UriInfo} from the request.
   * @return The {@link Set} of {@link SzEntityIdentifier} instances that was
   * parsed from the specified parameters.
   */
  static Set<SzEntityIdentifier> parseEntityIdentifiers(
      List<String> params,
      String paramName,
      SzHttpMethod httpMethod,
      UriInfo uriInfo,
      Timers timers) {
    Set<SzEntityIdentifier> result = new LinkedHashSet<>();

    // check if the params is null or missing
    if (params == null || params.size() == 0) {
      return result;
    }

    // iterate over the params
    for (String param : params) {
      try {
        SzEntityIdentifier id = SzEntityIdentifier.valueOf(param);
        result.add(id);

      } catch (Exception e) {
        throw newBadRequestException(
            httpMethod, uriInfo, timers,
            "Improperly formatted entity identifier parameter: "
                + paramName + "=" + param);
      }
    }

    return result;
  }

  /**
   * Gets the flags to use given the specified parameters.
   *
   * @param forceMinimal Whether or not minimal format is forced.
   *
   * @param featureMode The {@link SzFeatureMode} describing how features
   *                    are retrieved.
   *
   * @param withFeatureStats Whether or not feature stats should be included.
   *
   * @param withInternalFeatures Whether or not to include internal features.
   *
   * @param withRelationships Whether or not to include relationships.
   *
   * @return The flags to use given the parameters.
   */
  static int getFlags(boolean             forceMinimal,
                      SzFeatureMode       featureMode,
                      boolean             withFeatureStats,
                      boolean             withInternalFeatures,
                      boolean             withRelationships)
  {
    return getFlags(0,
                    forceMinimal,
                    featureMode,
                    withFeatureStats,
                    withInternalFeatures,
                    withRelationships);
  }

  /**
   * Gets the flags to use given the specified parameters.
   *
   * @param baseFlags The base flags to start out with.
   *
   * @param forceMinimal Whether or not minimal format is forced.
   *
   * @param featureMode The {@link SzFeatureMode} describing how features
   *                    are retrieved.
   *
   * @param withFeatureStats Whether or not feature stats should be included.
   *
   * @param withInternalFeatures Whether or not to include internal features.
   *
   * @param withRelationships Whether or not to include relationships.
   *
   * @return The flags to use given the parameters.
   */
  static int getFlags(int                 baseFlags,
                      boolean             forceMinimal,
                      SzFeatureMode       featureMode,
                      boolean             withFeatureStats,
                      boolean             withInternalFeatures,
                      boolean             withRelationships)
  {
    int flags = baseFlags
              | G2_ENTITY_INCLUDE_RECORD_DATA
              | G2_SEARCH_INCLUDE_FEATURE_SCORES; // for searches

    // check for relationships
    flags |= withRelationships ? G2_ENTITY_INCLUDE_ALL_RELATIONS : 0;

    // check if forcing minimal format
    if (forceMinimal) {
      // minimal format, not much else to do
      return flags;
    }

    // add the standard flags
    flags |= (G2_ENTITY_INCLUDE_ENTITY_NAME | DEFAULT_RECORD_FLAGS);

    // add the standard relationship flags
    if (withRelationships) {
      flags |= G2_ENTITY_INCLUDE_RELATED_ENTITY_NAME
            | G2_ENTITY_INCLUDE_RELATED_RECORD_SUMMARY
            | G2_ENTITY_INCLUDE_RELATED_MATCHING_INFO;
    }

    // add the feature flags
    if (featureMode != NONE) {
      // get represenative features
      flags |= G2_ENTITY_INCLUDE_REPRESENTATIVE_FEATURES;

      // check if feature stats are requested
      if (withFeatureStats) {
        flags |= G2_ENTITY_OPTION_INCLUDE_FEATURE_STATS;
      }

      // check if internal features are requested
      if (withInternalFeatures) {
        flags |= G2_ENTITY_OPTION_INCLUDE_INTERNAL_FEATURES;
      }
    }
    return flags;
  }

  /**
   * Post-processes the entity data according to the specified parameters.
   *
   * @param entityData The {@link SzEntityData} to modify.
   *
   * @param forceMinimal Whether or not minimal format is forced.
   *
   * @param featureMode The {@link SzFeatureMode} describing how features
   *                    are retrieved.
   */
  static void postProcessEntityData(SzEntityData  entityData,
                                    boolean       forceMinimal,
                                    SzFeatureMode featureMode)
  {
    // check if we need to strip out duplicate features
    if (featureMode == REPRESENTATIVE) {
      stripDuplicateFeatureValues(entityData);
    }

    // check if fields are going to be null if they would otherwise be set
    if (featureMode == NONE || forceMinimal) {
      setEntitiesPartial(entityData);
    }
  }

  /**
   * Sets the partial flags for the resolved entity and related
   * entities in the {@link SzEntityData}.
   */
  static void setEntitiesPartial(SzEntityData entityData) {
    entityData.getResolvedEntity().setPartial(true);
    entityData.getRelatedEntities().forEach(e -> {
      e.setPartial(true);
    });
  }

  /**
   * Strips out duplicate feature values for each feature in the resolved
   * and related entities of the specified {@link SzEntityData}.
   */
  static void stripDuplicateFeatureValues(SzEntityData entityData) {
    stripDuplicateFeatureValues(entityData.getResolvedEntity());
    List<SzRelatedEntity> relatedEntities = entityData.getRelatedEntities();
    if (relatedEntities != null) {
      relatedEntities.forEach(e -> stripDuplicateFeatureValues(e));
    }
  }

  /**
   * Strips out duplicate feature values in the specified {@link
   * SzResolvedEntity}.
   */
  static void stripDuplicateFeatureValues(SzResolvedEntity entity) {
    Map<String, List<SzEntityFeature>> featureMap = entity.getFeatures();
    if (featureMap != null) {
      featureMap.values().forEach(list -> {
        list.forEach(f -> f.setDuplicateValues(null));
      });
    }
  }


  static Timers newTimers() {
    return new Timers("overall");
  }

  static void processingRawData(Timers timers) {
    if (timers != null) timers.start("processRawData");
  }

  static void processedRawData(Timers timers) {
    if (timers != null) timers.pause("processRawData");
  }

  static void callingNativeAPI(Timers timers, String api, String function) {
    if (timers == null) return;
    timers.start("nativeAPI",
                 "nativeAPI:" + api + "." + function);
  }

  static void calledNativeAPI(Timers timers, String api, String function) {
    if (timers == null) return;
    timers.pause("nativeAPI",
                 "nativeAPI:" + api + "." + function);
  }

  static void enteringQueue(Timers timers) {
    if (timers != null) timers.start("enqueued");
  }

  static void exitingQueue(Timers timers) {
    if (timers != null) timers.pause("enqueued");
  }

  static void obtainingLock(Timers timers, String lockName) {
    if (timers != null) timers.start("locking",
                                     "locking: " + lockName);
  }

  static void obtainedLock(Timers timers, String lockName) {
    if (timers != null) timers.pause("locking",
                                     "locking: " + lockName);
  }

  /**
   * Ensures that loading of records is allowed and if not throws a
   * {@link ForbiddenException}.
   *
   * @param provider The {@link SzApiProvider} to check for read-only mode.
   * @param method The {@link HttpMethod} used.
   * @param uriInfo The {@link UriInfo} for the request path.
   * @param timers The {@link Timers} being used by the request handler.
   *
   * @throws ForbiddenException If the specified {@link SzApiProvider} is in
   *                            read-only mode.
   */
  public static void ensureLoadingIsAllowed(SzApiProvider provider,
                                            SzHttpMethod  method,
                                            UriInfo       uriInfo,
                                            Timers        timers)
      throws ForbiddenException
  {
    if (provider.isReadOnly()) {
      throw newForbiddenException(
          method, uriInfo, timers,
          "Loading data is not allowed if Senzing API Server started "
              + "in read-only mode");
    }
  }

  /**
   * Ensures that changing the configuration is allowed and if not throws a
   * {@link ForbiddenException}.
   *
   * @param provider The {@link SzApiProvider} to check for read-only mode and
   *                 admin mode.
   * @param method The {@link HttpMethod} used.
   * @param uriInfo The {@link UriInfo} for the request path.
   * @param timers The {@link Timers} being used by the request handler.
   *
   * @throws ForbiddenException If the specified {@link SzApiProvider} is in
   *                            read-only mode.
   */
  public static void ensureConfigChangesAllowed(SzApiProvider provider,
                                                SzHttpMethod  method,
                                                UriInfo       uriInfo,
                                                Timers        timers)
      throws ForbiddenException
  {
    if (!provider.isAdminEnabled()) {
      throw newForbiddenException(
          method, uriInfo, timers,
          "Configuration changes are not allowed if Senzing API Server is not "
              + "started with admin functions enabled.");
    }
    if (provider.isReadOnly()) {
      throw newForbiddenException(
          method, uriInfo, timers,
          "Configuration changes are not allowed if Senzing API Server started "
              + "in read-only mode");
    }
    G2ConfigMgr configMgrApi = provider.getConfigMgrApi();
    if (configMgrApi == null) {
      throw newForbiddenException(
          method, uriInfo, timers,
          "Configuration changes are not allowed if Senzing API Server is "
              + "using a static configuration specified by the G2CONFIGFILE "
              + "initialization parameter or if the server is locked to a "
              + "specific configuration ID in the database.");
    }
  }

  /**
   * Formats a test-info string using the URI text and the body content.
   * @param uriText
   * @param bodyContent
   * @return The formatted string.
   */
  public static String formatTestInfo(String uriText, String bodyContent)
  {
    return "uriText=[ " + uriText + " ], bodyContent=[ " + bodyContent + " ]";
  }

  /**
   * Generates a 64-bit hash from the system identity hash codes of the two
   * specified objects.
   *
   * @param obj1 The first object whose identity hash code goes to the high-order
   *             bits.
   * @param obj2 The second object whose identity hash code goes to the low-order
   *             bits.
   *
   * @return The <tt>long</tt> hash code of the object pair.
   */
  public static long identityPairHash(Object obj1, Object obj2) {
    long high = (long) ((obj1 == null) ? 0 : System.identityHashCode(obj1));
    long low = (long) ((obj2 == null) ? 0 : System.identityHashCode(obj2));
    return (high << 32) | low;
  }

  /**
   * Logs an error related to sending asynchronous info messages.
   *
   * @param e The {@link Exception} that occurred.
   * @param message The info message that failed.
   */
  public static final void logFailedAsyncInfo(Exception e, SzMessage message) {
    // check what was previously logged and avoid double-logging
    Long previous = LAST_INFO_ERROR_HASH.get();
    long hash = identityPairHash(e, message);
    if (previous != null && previous == hash) return;
    LAST_INFO_ERROR_HASH.set(hash);

    Date timestamp = new Date();
    String info = message.getBody();
    System.err.println(
        timestamp + ": FAILED TO SEND ASYNC INFO MESSAGE: " + info);
    synchronized (INFO_ERROR_SUPPRESSOR) {
      boolean suppressing = INFO_ERROR_SUPPRESSOR.isSuppressing();
      ErrorLogSuppressor.Result result = INFO_ERROR_SUPPRESSOR.updateOnError();
      switch (result.getState()) {
        case SUPPRESSED:
          if (!suppressing) {
            System.err.println(
                timestamp + ": SUPPRESSING ASYNC INFO MESSAGE ERRORS FOR "
                    + INFO_ERROR_SUPPRESSOR.getSuppressDuration() + "ms");
          }
          break;
        case REACTIVATED:
          if (result.getSuppressedCount() > 0) {
            System.err.println(
                timestamp + ": RESUMING ASYNC INFO MESSAGE ERRORS ("
                    + result.getSuppressedCount() + " SUPPRESSED)");
          }
        case ACTIVE:
          e.printStackTrace();
          break;
      }
    }
  }

  /**
   * Creates a proxy {@link UriInfo} using the Web Socket {@link Session} to
   * back it.
   *
   * @param webSocketSession The Web Socket {@link Session}.
   *
   * @return The proxied {@link UriInfo} object using the specified Web Socket
   *                     {@link Session}.
   */
  protected static UriInfo newProxyUriInfo(Session webSocketSession) {
    try {
      InvocationHandler handler = (p, m, a) -> {
        switch (m.getName()) {
          case "getRequestUri":
            return webSocketSession.getRequestURI();
          case "getQueryParameters": {
            MultivaluedMap<String, String> result = new MultivaluedHashMap<>();
            Map<String, List<String>> paramMap
                = webSocketSession.getRequestParameterMap();
            paramMap.forEach((key, values) -> {
              result.addAll(key, values);
            });
            return result;
          }
          case "getPathParameters": {
            MultivaluedMap<String, String> result = new MultivaluedHashMap<>();
            Map<String, String> paramMap = webSocketSession.getPathParameters();
            paramMap.forEach((key, value) -> {
              result.add(key, value);
            });
            return result;
          }
          default:
            throw new UnsupportedOperationException(
                "Operation not implemented on Web Socket proxy UriInfo");
        }
      };

      ClassLoader loader = ServicesUtil.class.getClassLoader();
      Class[] classes = {UriInfo.class};

      return (UriInfo) Proxy.newProxyInstance(loader, classes, handler);

    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
