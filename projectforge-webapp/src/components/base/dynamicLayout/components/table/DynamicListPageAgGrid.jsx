import PropTypes from 'prop-types';
import React, { useState } from 'react';
import { Button } from '../../../../design';
import { fetchJsonPost } from '../../../../../utilities/rest';
import history from '../../../../../utilities/history';
import DynamicAgGrid from './DynamicAgGrid';

function DynamicListPageAgGrid({
    columnDefs,
    id,
    rowSelection,
    rowMultiSelectWithClick,
    rowClickRedirectUrl,
    onColumnStatesChangedUrl,
    multiSelectButtonTitle,
    urlAfterMultiSelect,
    pagination,
    paginationPageSize,
    getRowClass,
}) {
    const [gridApi, setGridApi] = useState();

    const onGridApiReady = (api) => {
        setGridApi(api);
    };

    const handleClick = React.useCallback((event) => {
        event.preventDefault();
        event.stopPropagation();
        const selectedIds = gridApi.getSelectedRows().map((item) => item.id);
        fetchJsonPost(urlAfterMultiSelect,
            { selectedIds },
            (json) => {
                const { url } = json;
                history.push(url);
            });
    }, [gridApi]);

    // getSelectedNodes
    return React.useMemo(() => (
        <div>
            {multiSelectButtonTitle && (
                // Show this button only for multi selection with e. g. mass update:
                <Button
                    id="next"
                    onClick={handleClick}
                    color="success"
                    outline
                >
                    {multiSelectButtonTitle}
                </Button>
            )}
            <DynamicAgGrid
                onGridApiReady={onGridApiReady}
                columnDefs={columnDefs}
                id={id}
                rowSelection={rowSelection}
                rowMultiSelectWithClick={rowMultiSelectWithClick}
                rowClickRedirectUrl={rowClickRedirectUrl}
                onColumnStatesChangedUrl={onColumnStatesChangedUrl}
                pagination={pagination}
                paginationPageSize={paginationPageSize}
                getRowClass={getRowClass}
            />
        </div>
    ),
    [
        handleClick,
        multiSelectButtonTitle,
        columnDefs,
        rowSelection,
        rowMultiSelectWithClick,
    ]);
}

DynamicListPageAgGrid.propTypes = {
    columnDefs: PropTypes.arrayOf(PropTypes.shape({
        id: PropTypes.string,
        title: PropTypes.string,
        titleIcon: PropTypes.arrayOf(PropTypes.string),
    })).isRequired,
    id: PropTypes.string,
    rowSelection: PropTypes.string,
    rowMultiSelectWithClick: PropTypes.bool,
    rowClickRedirectUrl: PropTypes.string,
    onColumnStatesChangedUrl: PropTypes.string,
    multiSelectButtonTitle: PropTypes.string,
    urlAfterMultiSelect: PropTypes.string,
    pagination: PropTypes.bool,
    paginationPageSize: PropTypes.number,
    getRowClass: PropTypes.string,
};

DynamicListPageAgGrid.defaultProps = {
    id: undefined,
    getRowClass: undefined,
};

export default DynamicListPageAgGrid;
