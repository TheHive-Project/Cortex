'use strict';

export default class PageController {
  constructor(id) {
    'ngInject';

    this.id = id;

    this.state = {
      filters: {},
      pagination: {
        pageSize: 50,
        current: 1
      }
    };
  }

  buildRange() {
    let page = this.pagination.current,
      size = this.pagination.pageSize;

    return `${(page - 1) * size}-${(page - 1) * size + size}`;
  }

  applyFilters() {
    this.state.filters = this.filters;
    this.localStorageService.set(this.id, this.state);
    this.load(1);
  }

  clearFilter(filterName) {
    this.filters[filterName] = _.isArray(this.filters[filterName]) ? [] : null;
    this.applyFilters();
  }

  clearFilters() {
    _.forEach(_.keys(this.filters), key => {
      this.filters[key] = _.isArray(this.filters[key]) ? [] : null;
    });

    this.applyFilters();
  }

  buildQuery() {}
  load() {}
}
