'user strict';

import _ from "lodash";

export default class AlertService {
    constructor($http, $interval) {
        'ngInject';

        this.$http = $http;
        this.$interval = $interval;
        this.update = 0;
        this.alerts = [];
    }

    startUpdate() {
        this.update += 1;
        if (!this.timer) {
            this.timer = this.$interval(this.updateAlerts, 10000, 0, true, this);
        }
    }

    stopUpdate() {
        this.update -= 1;
        if (this.update <= 0 && this.$interval) {
            this.$interval.cancel(this.timer);
            delete this.$interval;
        }
    }

    updateAlerts(self) {
        self.$http.get('./api/alert').then(
            response => {
                self.alerts = response.data;
            },
            () => {
                self.alerts = [];
            }
        );
    }

    contains(alertType) {
        return _.find(this.alerts, { type: alertType });
    }

    nonEmpty() {
        return this.alerts.length > 0;
    }

    isEmpty() {
        return this.alerts.length === 0;
    }
}