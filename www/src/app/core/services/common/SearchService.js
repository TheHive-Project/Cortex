'use strict';

import _ from 'lodash';

export default class SearchService {
  constructor($log, $http, $q) {
    'ngInject';

    this.$log = $log;
    this.$http = $http;
    this.$q = $q;

    this.config = {};
  }

  /**
   * @param {*} config
   * {
   *  filter:{},
   *  objectType: '',
   *  range: 'all',
   *  sort: [],
   *  nparent: 1,
   *  nstats: true
   * }
   */
  configure(config) {
    this.config = _.assign({}, config);

    // Set the URL
    if (this.config.url) {
      return this;
    }

    if (!_.isString(config.objectType) || config.objectType === 'any') {
      this.config.url = './api/_search';
    } else {
      this.config.url = `./api/${config.objectType.replace(/_/g, '/')}/_search`;
    }

    return this;
  }

  buildQueryString() {
    // Set the URL
    if (this.config.objectType) {
      if (
        !_.isString(this.config.objectType) ||
        this.config.objectType === 'any'
      ) {
        this.config.url = './api/_search';
      } else {
        this.config.url = `./api/${this.config.objectType.replace(
          /_/g,
          '/'
        )}/_search`;
      }
    }

    let queryString = [];

    // Set the range for pagination
    if (_.isString(this.config.range)) {
      queryString.push('range=' + encodeURIComponent(this.config.range));
    }

    // Set the sort options
    if (_.isString(this.config.sort)) {
      queryString.push('sort=' + encodeURIComponent(this.config.sort));
    } else if (_.isArray(this.config.sort)) {
      _.forEach(this.config.sort, s => {
        queryString.push('sort=' + encodeURIComponent(s));
      });
    }

    // Set the parent option
    if (_.isNumber(this.config.nparent)) {
      queryString.push('nparent=' + this.config.nparent);
    }

    // Set the stats option
    if (this.config.nstats === true) {
      queryString.push('nstats=' + this.config.nstats);
    }

    return queryString.join('&');
  }

  search() {
    const queryString = this.buildQueryString();

    return this.$http
      .post(this.config.url + (queryString ? `?${queryString}` : ''), {
        query: this.config.filter
      })
      .then(response => {
        response.total = parseInt(response.headers('X-Total'));
        return this.$q.resolve(response);
      })
      .catch(err => this.$q.reject(err));
  }
}
