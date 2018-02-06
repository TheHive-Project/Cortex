'use strict';

import _ from 'lodash';

export default class JobsController {
  constructor(
    $log,
    $scope,
    $state,
    $interval,
    $uibModal,
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
    this.$uibModal = $uibModal;
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

    this.filters = this.localStorageService.get('job-filters') || {
      search: null,
      status: [],
      dataType: [],
      analyzer: []
    };
    this.pagination = {
      pageSize: 50,
      current: 1
    };
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

    if (!_.isEmpty(this.filters.dataType)) {
      criteria.push({
        _in: { _field: 'dataType', _values: this.filters.dataType }
      });
    }

    if (!_.isEmpty(this.filters.analyzer)) {
      criteria.push({
        _in: {
          _field: 'analyzerId',
          _values: _.map(this.filters.analyzer, 'id')
        }
      });
    }

    return _.isEmpty(criteria) ? {} : { _and: criteria };
  }

  buildRange() {
    let page = this.pagination.current,
      size = this.pagination.pageSize;

    return `${(page - 1) * size}-${(page - 1) * size + size}`;
  }

  applyFilters() {
    this.localStorageService.set('job-filters', this.filters);
    this.load(1);
  }

  clearFilter(filterName) {
    this.filters[filterName] = _.isArray(this.filters[filterName]) ? [] : null;
    this.applyFilters();
  }

  load(page) {
    if (page) {
      this.pagination.current = 1;
    }
    // let post = {
    //   limit: this.pagination.itemsPerPage,
    //   start: page - 1,
    //   dataTypeFilter: this.search.dataType || null,
    //   analyzerFilter: this.search.analyzerId || null,
    //   dataFilter: this.search.data || null
    // };

    // const post = {
    //   query: this.buildQuery(),
    //   range: 'all'
    // };

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

  pageChanged() {
    this.load(this.pagination.current);
  }

  filterJobs(element) {
    let conditions = [];

    if (this.search.analyzerId) {
      conditions.push(element.analyzerId === this.search.analyzerId);
    }

    if (this.search.dataType) {
      conditions.push(
        element.artifact.attributes.dataType === this.search.dataType
      );
    }

    if (this.search.data) {
      let data = this.search.data;
      let attrs = element.artifact.attributes;
      let regex = new RegExp(data, 'gi');
      let artifact =
        attrs.dataType === 'file' ? attrs.filename : element.artifact.data;

      conditions.push(regex.test(artifact));
    }

    return conditions.indexOf(false) < 0;
  }

  clearDataFilter() {
    this.search.data = '';
    this.load(1);
  }

  remove(id) {
    let modalInstance = this.$uibModal.open({
      animation: true,
      templateUrl: 'views/jobs.delete.html',
      controller: 'JobDeleteCtrl',
      controllerAs: 'vm',
      resolve: {
        jobId: () => id
      }
    });

    modalInstance.result.then(jobId => this.JobService.remove(jobId)).then(
      /*response*/ () => {
        this.load(1);
        this.NotificationService.success('Job removed successfully');
      }
    );
  }

  checkJobs(self) {
    if (self.running !== -1) {
      self.load(self.pagination.current);
    }
  }
}
