/* global BUILDCONFIG */

class DatasourceListController {
    constructor(
        $scope, $state, $stateParams, $filter,
        datasourceService, modalService, paginationService
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
        this.pageSize = 10;
    }

    $onInit() {
        this.BUILDCONFIG = BUILDCONFIG;
        this.currentOwnershipFilter = this.$state.params.ownership;
        this.$scope.$watch('$ctrl.currentOwnershipFilter', (current, last) => {
            if (current !== last || !this.pagination) {
                this.fetchPage();
            }
        });
    }

    shouldShowPlaceholder() {
        return !this.currentQuery &&
            !this.fetchError &&
            (!this.search || !this.search.length) &&
            this.datasources &&
            this.datasources.length === 0;
    }

    shouldShowEmptySearch() {
        return !this.currentQuery &&
            !this.fetchError &&
            this.search && this.search.length &&
            this.datasources && !this.datasources.length;
    }

    fetchPage(page = this.$state.params.page || 1, search = this.$state.params.search) {
        this.search = search && search.length ? search : null;
        delete this.fetchError;
        this.datasources = [];
        let currentQuery = this.datasourceService.query({
            sort: 'createdAt,desc',
            pageSize: this.pageSize,
            page: page - 1,
            search: this.search,
            ownershipType: this.currentOwnershipFilter
        }).then(paginatedResponse => {
            this.datasources = paginatedResponse.results;
            this.pagination = this.paginationService.buildPagination(paginatedResponse);
            this.paginationService.updatePageParam(page, this.search, null, {
                ownership: this.currentOwnershipFilter
            });
            if (this.currentQuery === currentQuery) {
                delete this.fetchError;
            }
        }, (e) => {
            if (this.currentQuery === currentQuery) {
                this.fetchError = e;
            }
        }).finally(() => {
            if (this.currentQuery === currentQuery) {
                delete this.currentQuery;
            }
        });
        this.currentQuery = currentQuery;
    }

    createDatasourceModal() {
        this.modalService.open({
            component: 'rfDatasourceCreateModal'
        }).result.then(() => {
            this.search = '';
            this.fetchPage(1, '');
        }).catch(() => {});
    }
}

const DatasourcesListModule = angular.module('pages.imports.datasources.list', []);

DatasourcesListModule.controller('DatasourceListController', DatasourceListController);

export default DatasourcesListModule;
