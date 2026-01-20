<#-- Global Search Dropdown -->
<li class="nav-item" id="global-search-container">
    <a class="nav-link"
       type="button"
       id="global-search-toggle"
       aria-expanded="false"
       title="Search"
       style="cursor: pointer;">
        🔍
    </a>
    <div class="search-dropdown-menu"
         id="navbar-search-dropdown"
         aria-labelledby="global-search-toggle">
        <div style="padding: 10px;">
            <input
                    type="search"
                    id="global-search-input"
                    class="form-control form-control-sm"
                    placeholder="Search topics, clusters, ACLs, quotas..."
                    autocomplete="off"
                    title="Search query"
                    style="border-radius: 20px;"
            />
        </div>
        <div id="global-search-dropdown"
             style="max-height: 500px; overflow-y: auto;">
            <!-- Search results populated by JavaScript -->
        </div>
    </div>
</li>

