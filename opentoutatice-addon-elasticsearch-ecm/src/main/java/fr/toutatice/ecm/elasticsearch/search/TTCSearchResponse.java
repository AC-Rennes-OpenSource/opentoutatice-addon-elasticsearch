package fr.toutatice.ecm.elasticsearch.search;

import org.elasticsearch.action.search.SearchResponse;

public class TTCSearchResponse {

	private int pageSize;
	private int currentPageIndex;
	private SearchResponse searchResponse;

	public TTCSearchResponse(SearchResponse searchResponse, int pageSize, int currentPageIndex) {
		this.pageSize = pageSize;
		this.searchResponse = searchResponse;
		this.currentPageIndex = currentPageIndex;
	}
	
	public int getPageSize() {
		return pageSize;
	}
	
	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
	}

	public int getCurrentPageIndex() {
		return currentPageIndex;
	}

	public void setCurrentPageIndex(int currentPageIndex) {
		this.currentPageIndex = currentPageIndex;
	}

	public SearchResponse getSearchResponse() {
		return searchResponse;
	}

	public void setSearchResponse(SearchResponse searchResponse) {
		this.searchResponse = searchResponse;
	}

}
