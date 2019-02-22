import tpl from './index.html';

class AoiDrawToolbarController {
    constructor($rootScope, $scope, $state, $log, $timeout) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.setComponentStyle();
    }

    setComponentStyle() {
        const height = angular.element(document.querySelector('.navbar'))[0].offsetHeight;
        this.eleStyle = {
            top: `${-height}px`,
            height: `${height}px`
        };
        this.leftStyle = {height: `${height}px`};
    }
}

const component = {
    bindings: {
    },
    templateUrl: tpl,
    controller: AoiDrawToolbarController.name
};

export default angular
    .module('components.projects.aoiDrawToolbar', [])
    .controller(AoiDrawToolbarController.name, AoiDrawToolbarController)
    .component('rfAoiDrawToolbar', component)
    .name;
