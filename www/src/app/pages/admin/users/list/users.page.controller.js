'use strict';

import PageController from '../../../../core/controllers/PageController';

export default class UsersPageController extends PageController {
  constructor($log, UserService, SearchService, localStorageService) {
    'ngInject';

    super('users-page');

    this.$log = $log;
    this.UserService = UserService;
    this.SearchService = SearchService;
    this.localStorageService = localStorageService;

    this.pagination = {
      current: 1,
      total: 0
    };

    this.state = this.localStorageService.get('users-page') || {
      filters: {
        search: null,
        status: []
      },
      pagination: {
        pageSize: 50,
        current: 1
      }
    };

    this.filters = this.state.filters;
    this.pagination = this.state.pagination;
    this.statuses = ['Active', 'Locked'];
  }

  $onInit() {
    this.load(1);
  }

  buildQuery() {
    let criteria = [];

    if (!_.isEmpty(this.filters.search)) {
      criteria.push({
        _or: [
          {
            _like: {
              _field: 'name',
              _value: this.filters.search
            }
          },
          {
            _field: '_id',
            _value: this.filters.search
          }
        ]
      });
    }

    if (!_.isEmpty(this.filters.status)) {
      criteria.push({
        _in: {
          _field: 'status',
          _values: _.map(this.filters.status, f => (f === 'Active' ? 'Ok' : f))
        }
      });
    }

    return _.isEmpty(criteria)
      ? {}
      : criteria.length === 1
        ? criteria[0]
        : {
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
    this.localStorageService.set('users-page', this.state);

    this.SearchService.configure({
      objectType: 'user',
      filter: this.buildQuery(),
      range: this.buildRange(),
      sort: '+name'
    })
      .search()
      .then(response => {
        this.users = response.data;
        this.pagination.total = parseInt(response.headers('x-total')) || 0;
      });
  }

  reload() {
    this.load(1);
  }

  newUser() {
    this.UserService.openModal(null, 'create', {})
      .then(() => {
        this.reload();
        //mode;
        this.NotificationService.success(`User created successfully`);
      })
      .catch(rejection => {
        if (rejection && rejection.type === 'ConflictError') {
          // Handle user uniquness
        }
      });
  }
}
