'use strict';

import _ from 'lodash';

export default class JobsController {
  constructor(
    $log,
    $scope,
    $state,
    $interval,
    AnalyzerService,
    JobService,
    SearchService,
    NotificationService
  ) {
    'ngInject';

    this.$log = $log;
    this.$scope = $scope;
    this.$state = $state;
    this.$interval = $interval;
    this.NotificationService = NotificationService;
    this.SearchService = SearchService;
    this.JobService = JobService;

    this.datatypes = _.keys(AnalyzerService.getTypes());
    this.jobs = [];
    this.running = 0;
    this.pagination = {
      current: 1,
      total: 0,
      itemsPerPage: 50
    };

    this.search = {
      analyzerId: '',
      data: '',
      dataType: ''
    };

    this.filters = {
      search: null,
      status: [],
      dataType: [],
      analyzer: []
    };
  }

  $onInit() {
    this.load(1);

    this.timer = this.$interval(this.checkJobs, 10000, 0, true, this);

    this.$scope.$on('$destroy', () => {
      this.$interval.cancel(this.timer);
    });

    // this.SearchService.configure({
    //   objectType: 'analyzer',
    //   filter: {
    //     _field: 'dataTypeList',
    //     _value: 'mail'
    //   },
    //   range: 'all'
    // }).search();
  }

  load(page) {
    if (!page) {
      page = 1;
    }
    let post = {
      limit: this.pagination.itemsPerPage,
      start: page - 1,
      dataTypeFilter: this.search.dataType || null,
      analyzerFilter: this.search.analyzerId || null,
      dataFilter: this.search.data || null
    };

    this.JobService.list(post).then(response => {
      this.jobs = response.data;
      this.running = _.findIndex(
        this.jobs,
        o => o.status === 'InProgress' || o.status === 'Waiting'
      );
      this.pagination.total = parseInt(response.headers('x-total')) || 0;
      this.pagination.current = page;
    });
  }

  pageChanged(page) {
    this.load(page);
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

  filterByAnalyzer(analyzerId) {
    if (this.search.analyzerId === analyzerId) {
      this.search.analyzerId = '';
    } else {
      this.search.analyzerId = analyzerId;
    }
    this.load(1);
  }

  filterByType(type) {
    if (this.search.dataType === type) {
      this.search.dataType = '';
    } else {
      this.search.dataType = type;
    }
    this.load(1);
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
