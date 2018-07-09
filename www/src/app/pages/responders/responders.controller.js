'use strict';

import _ from 'lodash/core';

import PageController from '../../core/controllers/PageController';

export default class RespondersController extends PageController {
  constructor(
    $log,
    SearchService,
    ResponderService,
    NotificationService,
    localStorageService
  ) {
    'ngInject';

    super('responders-page');

    this.$log = $log;
    this.ResponderService = ResponderService;
    this.NotificationService = NotificationService;
    this.localStorageService = localStorageService;
    this.SearchService = SearchService;

    this.pagination = {
      current: 1,
      total: 0
    };

    this.state = this.localStorageService.get('responders-page') || {
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
        _or: [{
            _like: {
              _field: 'description',
              _value: this.filters.search
            }
          },
          {
            _like: {
              _field: 'name',
              _value: this.filters.search
            }
          }
        ]
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

    return _.isEmpty(criteria) ? {} :
      criteria.length === 1 ?
      criteria[0] : {
        _and: criteria
      };
  }

  load(page) {
    if (page) {
      this.pagination.current = page;
    }

    this.state.filters = this.filters;
    this.state.pagination = {
      pageSize: this.pagination.pageSize
    };
    this.localStorageService.set('responders-page', this.state);

    this.SearchService.configure({
        objectType: 'responder',
        filter: this.buildQuery(),
        range: this.buildRange(),
        sort: '+name'
      })
      .search()
      .then(response => {
        this.responders = response.data;
        this.pagination.total = parseInt(response.headers('x-total')) || 0;
      });
  }
}