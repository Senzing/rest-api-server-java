package com.senzing.api.model;

import com.senzing.util.Timers;

import javax.ws.rs.core.UriInfo;
import java.util.*;

/**
 * The response containing a list of {@link SzAttributeSearchResult} instances
 * describing the search results.
 *
 */
public class SzAttributeSearchResponse extends SzResponseWithRawData
{
  /**
   * The data for this instance.
   */
  private Data data = new Data();

  /**
   * Package-private default constructor.
   */
  SzAttributeSearchResponse() {
    this.data.searchResults = null;
  }

  /**
   * Constructs with only the HTTP method and the self link, leaving the
   * license info data to be initialized later.
   *
   * @param httpMethod The {@link SzHttpMethod}.
   *
   * @param httpStatusCode The HTTP response status code.
   *
   * @param selfLink The string URL link to generate this response.
   *
   * @param timers The {@link Timers} object for the timings that were taken.
   */
  public SzAttributeSearchResponse(SzHttpMethod httpMethod,
                                   int          httpStatusCode,
                                   String       selfLink,
                                   Timers       timers)
  {
    super(httpMethod, httpStatusCode, selfLink, timers);
    this.data.searchResults = new LinkedList<>();
  }

  /**
   * Constructs with only the HTTP method and the {@link UriInfo}, leaving the
   * license info data to be initialized later.
   *
   * @param httpMethod The {@link SzHttpMethod}.
   *
   * @param httpStatusCode The HTTP response status code.
   *
   * @param uriInfo The {@link UriInfo} associated with the request.
   *
   * @param timers The {@link Timers} object for the timings that were taken.
   */
  public SzAttributeSearchResponse(SzHttpMethod httpMethod,
                                   int          httpStatusCode,
                                   UriInfo      uriInfo,
                                   Timers       timers)
  {
    super(httpMethod, httpStatusCode, uriInfo, timers);
    this.data.searchResults = new LinkedList<>();
  }

  /**
   * Returns the {@link Data} for this instance.
   *
   * @return The {@link Data} for this instance.
   */
  public Data getData() {
    return this.data;
  }

  /**
   * Sets the {@link List} of {@link SzAttributeSearchResult} instances to the
   * specified list of results.
   *
   * @param results The {@link List} of {@link SzAttributeSearchResult} results.
   */
  public void setSearchResults(List<SzAttributeSearchResult> results)
  {
    this.data.searchResults.clear();
    if (results != null) {
      this.data.searchResults.addAll(results);
    }
  }

  /**
   * Adds the specified {@link SzAttributeSearchResult} to the list of results.
   *
   * @param result The {@link SzAttributeSearchResult} result to add.
   */
  public void addSearchResult(SzAttributeSearchResult result) {
    this.data.searchResults.add(result);
  }

  /**
   * Inner class to represent the data section for this response.
   */
  public static class Data {
    /**
     * The list of {@link SzAttributeSearchResult} instances describing the
     * results.
     */
    private List<SzAttributeSearchResult> searchResults;

    /**
     * Private default constructor.
     */
    private Data() {
      // do nothing
    }

    /**
     * Gets the {@link List} of {@linkplain SzAttributeSearchResult search
     * results}.
     *
     * @return {@link List} of {@linkplain SzAttributeSearchResult search
     *          results}
     */
    public List<SzAttributeSearchResult> getSearchResults() {
      List<SzAttributeSearchResult> list = this.searchResults;
      return Collections.unmodifiableList(list);
    }
  }
}
