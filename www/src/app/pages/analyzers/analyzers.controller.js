'use strict';

import _ from 'lodash/core';

export default class AnalyzersController {
  constructor(
    $log,
    SearchService,
    AnalyzerService,
    NotificationService,
    localStorageService
  ) {
    'ngInject';
    this.$log = $log;
    this.AnalyzerService = AnalyzerService;
    this.NotificationService = NotificationService;
    this.localStorageService = localStorageService;
    this.SearchService = SearchService;

    this.pagination = {
      current: 1,
      total: 0
    };

    this.state = this.localStorageService.get('analyzers-page') || {
      filters: {
        search: null,
        dataType: []
      },
      pagination: {
        pageSize: 50,
        current: 1
      }
    };

    this.filters = this.state.filters;
    this.pagination = this.state.pagination;
  }

  $onInit() {
    this.load(1);
  }

  buildQuery() {
    let criteria = [];

    if (!_.isEmpty(this.filters.search)) {
      criteria.push({
        _like: {
          _field: 'description',
          _value: this.filters.search
        }
      });
    }

    if (!_.isEmpty(this.filters.dataType)) {
      criteria.push({
        _in: {
          _field: 'dataTypeList',
          _values: this.filters.dataType
        }
      });
    }

    return _.isEmpty(criteria)
      ? {}
      : {
          _and: criteria
        };
  }

  buildRange() {
    let page = this.pagination.current,
      size = this.pagination.pageSize;

    return `${(page - 1) * size}-${(page - 1) * size + size}`;
  }

  applyFilters() {
    this.state.filters = this.filters;
    this.localStorageService.set('analyzers-page', this.state);
    this.load(1);
  }

  clearFilter(filterName) {
    this.filters[filterName] = _.isArray(this.filters[filterName]) ? [] : null;
    this.applyFilters();
  }

  load(page) {
    if (page) {
      this.pagination.current = page;
    }

    this.state.filters = this.filters;
    this.state.pagination = {
      pageSize: this.pagination.pageSize
    };
    this.localStorageService.set('analyzers-page', this.state);

    this.SearchService.configure({
      objectType: 'analyzer',
      filter: this.buildQuery(),
      range: this.buildRange()
    })
      .search()
      .then(response => {
        this.analyzers = response.data;
        this.pagination.total = parseInt(response.headers('x-total')) || 0;
      });
  }
}
