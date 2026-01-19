<#-- @ftlvariable name="query" type="java.lang.String" -->
<#-- @ftlvariable name="searchResults" type="com.infobip.kafkistry.service.search.SearchResults" -->
<#-- @ftlvariable name="allCategories" type="com.infobip.kafkistry.service.search.SearchCategory[]" -->
<#-- @ftlvariable name="selectedCategories" type="java.util.Set<com.infobip.kafkistry.service.search.SearchCategory>" -->
<#-- @ftlvariable name="maxResults" type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="lastCommit" type="java.lang.String" -->

<html lang="en">
<head>
    <#include "../commonResources.ftl"/>
    <script src="static/search/search.js?ver=${lastCommit}"></script>
    <link rel="stylesheet" href="static/css/search.css?ver=${lastCommit}">
    <title>Kafkistry: Search</title>
    <meta name="current-nav" content="nav-search"/>
</head>

<body>

<#include "../commonMenu.ftl">

<#-- Macro to highlight matched terms in text -->
<#macro highlightMatches text matches>
    <#if matches?size == 0>
        ${text}
    <#else>
        <#assign result = text>
        <#list matches as match>
            <#-- Case-insensitive replacement with highlighting -->
            <#assign pattern = "(?i)(${match})">
            <#assign result = result?replace(pattern, '<mark class="search-highlight">$1</mark>', 'r')>
        </#list>
        ${result?no_esc}
    </#if>
</#macro>

<div class="container">
    <h1>Search</h1>

    <div class="row mt-3">
        <div class="col">
            <div class="input-group mb-3">
                <input
                    type="search"
                    id="search-input"
                    class="form-control"
                    placeholder="Search for topics, clusters, ACLs, quotas..."
                    value="${query}"
                    autofocus
                />
                <button class="btn btn-primary" type="button" id="search-btn">
                    Search
                </button>
            </div>
        </div>
        <div class="col-3">
            <button class="btn btn-outline-secondary w-100" data-bs-toggle="modal" data-bs-target="#filter-modal">
                Filter Categories
            </button>
        </div>
        <div class="col-1">
            <div class="form-group mb-3">
                <input
                    type="number"
                    id="max-results-input"
                    class="form-control"
                    placeholder="Max results"
                    value="${maxResults}"
                    title="Maximum results (blank = all)"
                    min="1"
                />
                <small class="form-text text-muted">Blank = all</small>
            </div>
        </div>
    </div>

    <#if searchResults??>
        <div class="search-results">
            <p class="text-muted">
                Found ${searchResults.totalResults} results in ${searchResults.executionTimeMs}ms
            </p>

            <#if searchResults.totalResults == 0>
                <div class="alert alert-info">
                    No results found for "<strong>${query}</strong>". Try different keywords.
                </div>
            <#else>
                <div class="list-group mt-3">
                    <#list searchResults.results as result>
                        <a href="${result.url}" class="list-group-item list-group-item-action">
                            <div class="d-flex w-100 justify-content-between">
                                <h6 class="mb-1">
                                    <@highlightMatches text=result.title matches=result.matches.titleMatches/>
                                    <span class="badge bg-secondary ml-2">${result.category.displayName}</span>
                                </h6>
                                <#if result.score gt 0.0 && result.score lte 1.0>
                                    <small class="text-muted">
                                        ${(result.score * 100)?round}% match
                                    </small>
                                </#if>
                            </div>
                            <#if result.subtitle??>
                                <p class="mb-1 text-muted small">
                                    <@highlightMatches text=result.subtitle matches=result.matches.subtitleMatches/>
                                </p>
                            </#if>
                            <#if result.description??>
                                <small class="text-muted">
                                    <@highlightMatches text=result.description matches=result.matches.descriptionMatches/>
                                </small>
                            </#if>
                        </a>
                    </#list>
                </div>
            </#if>
        </div>
    <#else>
        <div class="text-center mt-5">
            <p class="lead">Enter a search query to find topics, clusters, ACLs, quotas, and more.</p>
        </div>
    </#if>
</div>

<!-- Filter Modal -->
<div class="modal fade" id="filter-modal" tabindex="-1" role="dialog">
    <div class="modal-dialog" role="document">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title">Filter Categories</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
                <#list allCategories as category>
                    <div class="form-check">
                        <input
                            class="form-check-input category-filter"
                            type="checkbox"
                            value="${category.id}"
                            id="filter-${category.id}"
                            <#if selectedCategories?seq_contains(category)>checked</#if>
                        />
                        <label class="form-check-label" for="filter-${category.id}">
                            ${category.displayName}
                        </label>
                    </div>
                </#list>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
                <button type="button" class="btn btn-primary" id="apply-filters-btn">Apply Filters</button>
            </div>
        </div>
    </div>
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>
