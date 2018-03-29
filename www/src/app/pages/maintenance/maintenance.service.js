'use strict';

export default function() {
  let successState = 'app';

  this.setSuccessState = function(state) {
    successState = state;
  };

  function MaintenanceService() {
    this.getSuccessState = function() {
      return successState;
    };
  }

  this.$get = function() {
    return new MaintenanceService();
  };
}
