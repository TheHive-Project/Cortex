'use strict';

import _ from 'lodash';

class JobsDeleteController {}

export default class JobsController {
  constructor(
    $log,
    $scope,
    $state,
    $interval,
    AnalyzerService,
    JobService,
    SearchService,
    NotificationService,
    localStorageService
  ) {
    'ngInject';

    this.$log = $log;
    this.$scope = $scope;
    this.$state = $state;
    this.$interval = $interval;
    this.NotificationService = NotificationService;
    this.localStorageService = localStorageService;
    this.SearchService = SearchService;
    this.JobService = JobService;

    this.datatypes = _.keys(AnalyzerService.getTypes());
    this.jobs = [];
    this.running = 0;
    this.pagination = {
      current: 1,
      total: 0
    };

    this.search = {
      analyzerId: '',
      data: '',
      dataType: ''
    };

    this.state = this.localStorageService.get('jobs-page') || {
      filters: {
        search: null,
        status: [],
        dataType: [],
        analyzer: []
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

    this.timer = this.$interval(this.checkJobs, 10000, 0, true, this);

    this.$scope.$on('$destroy', () => {
      this.$interval.cancel(this.timer);
    });
  }

  buildQuery() {
    let criteria = [];

    if (!_.isEmpty(this.filters.search)) {
      criteria.push({
        _field: 'data',
        _value: this.filters.search
      });
    }

    if (!_.isEmpty(this.filters.dataType)) {
      criteria.push({
        _in: {
          _field: 'dataType',
          _values: this.filters.dataType
        }
      });
    }

    if (!_.isEmpty(this.filters.analyzer)) {
      criteria.push({
        _in: {
          _field: 'analyzerDefinitionId',
          _values: _.map(this.filters.analyzer, 'analyzerDefinitionId')
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
    this.localStorageService.set('jobs-page', this.state);
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
    this.localStorageService.set('jobs-page', this.state);

    this.SearchService.configure({
      objectType: 'job',
      filter: this.buildQuery(),
      range: this.buildRange()
    })
      .search()
      .then(response => {
        this.jobs = response.data;
        this.running = _.findIndex(
          this.jobs,
          o => o.status === 'InProgress' || o.status === 'Waiting'
        );
        this.pagination.total = parseInt(response.headers('x-total')) || 0;
        //this.pagination.current = page;
      });
  }

  checkJobs(self) {
    if (self.running !== -1) {
      self.load(self.pagination.current);
    }
  }
}
