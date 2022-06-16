import PropTypes from 'prop-types';
import React, { useState } from 'react';
import { Button } from '../../../../design';
import { fetchJsonPost, getServiceURL, handleHTTPErrors } from '../../../../../utilities/rest';
import history from '../../../../../utilities/history';
import DynamicAgGrid from './DynamicAgGrid';
import { DynamicLayoutContext } from '../../context';

function DynamicListPageAgGrid({
    columnDefs,
    id,
    sortModel,
    rowSelection,
    rowMultiSelectWithClick,
    rowClickRedirectUrl,
    onColumnStatesChangedUrl,
    multiSelectButtonTitle,
    urlAfterMultiSelect,
    handleCancelUrl,
    pagination,
    paginationPageSize,
    getRowClass,
    locale,
    dateFormat,
    thousandSeparator,
    decimalSeparator,
    timestampFormatSeconds,
    timestampFormatMinutes,
    currency,
}) {
    const [gridApi, setGridApi] = useState();
    const [columnApi, setColumnApi] = useState();

    const onGridApiReady = React.useCallback((api, colApi) => {
        setGridApi(api);
        setColumnApi(colApi);
    }, []);

    React.useEffect(() => {
        if (columnApi) {
            columnApi.resetColumnState();
            columnApi.applyColumnState({
                state: sortModel,
                defaultState: { sort: null },
            });
        }
    }, [columnApi, sortModel]);

    const { ui } = React.useContext(DynamicLayoutContext);

    const handleCancel = React.useCallback(() => {
        fetch(getServiceURL(handleCancelUrl), {
            method: 'GET',
            credentials: 'include',
        })
            .then(handleHTTPErrors)
            .then((response) => response.text())
            .then((url) => {
                history.push(url);
            });
    }, [gridApi]);

    const handleClick = React.useCallback(() => {
        const selectedIds = gridApi.getSelectedRows().map((item) => item.id);
        fetchJsonPost(
            urlAfterMultiSelect,
            { selectedIds },
            (json) => {
                const { url } = json;
                history.push(url);
            },
        );
    }, [gridApi]);

    // getSelectedNodes
    return React.useMemo(
        () => (
            <div>
                {multiSelectButtonTitle && (
                    // Show these buttons only for multi selection with e. g. mass update:
                    <>
                        <Button
                            id="cancel"
                            onClick={handleCancel}
                            color="danger"
                            outline
                        >
                            {ui.translations.cancel}
                        </Button>
                        <Button
                            id="next"
                            onClick={handleClick}
                            color="success"
                            outline
                        >
                            {multiSelectButtonTitle}
                        </Button>
                    </>
                )}
                <DynamicAgGrid
                    onGridApiReady={onGridApiReady}
                    columnDefs={columnDefs}
                    id={id}
                    sortModel={sortModel}
                    rowSelection={rowSelection}
                    rowMultiSelectWithClick={rowMultiSelectWithClick}
                    rowClickRedirectUrl={rowClickRedirectUrl}
                    onColumnStatesChangedUrl={onColumnStatesChangedUrl}
                    pagination={pagination}
                    paginationPageSize={paginationPageSize}
                    getRowClass={getRowClass}
                    locale={locale}
                    dateFormat={dateFormat}
                    thousandSeparator={thousandSeparator}
                    decimalSeparator={decimalSeparator}
                    timestampFormatSeconds={timestampFormatSeconds}
                    timestampFormatMinutes={timestampFormatMinutes}
                    currency={currency}
                />
            </div>
        ),
        [
            handleClick,
            multiSelectButtonTitle,
            columnDefs,
            sortModel,
            rowSelection,
            rowMultiSelectWithClick,
            ui,
        ],
    );
}

DynamicListPageAgGrid.propTypes = {
    columnDefs: PropTypes.arrayOf(PropTypes.shape({
        id: PropTypes.string,
        title: PropTypes.string,
        titleIcon: PropTypes.arrayOf(PropTypes.string),
    })).isRequired,
    id: PropTypes.string,
    sortModel: PropTypes.arrayOf(PropTypes.shape({
        colId: PropTypes.string.isRequired,
        sort: PropTypes.string,
        sortIndex: PropTypes.number,
    })),
    rowSelection: PropTypes.string,
    rowMultiSelectWithClick: PropTypes.bool,
    rowClickRedirectUrl: PropTypes.string,
    onColumnStatesChangedUrl: PropTypes.string,
    multiSelectButtonTitle: PropTypes.string,
    urlAfterMultiSelect: PropTypes.string,
    handleCancelUrl: PropTypes.string,
    pagination: PropTypes.bool,
    paginationPageSize: PropTypes.number,
    getRowClass: PropTypes.string,
    locale: PropTypes.string,
    dateFormat: PropTypes.string,
    thousandSeparator: PropTypes.string,
    decimalSeparator: PropTypes.string,
    timestampFormatSeconds: PropTypes.string,
    timestampFormatMinutes: PropTypes.string,
    currency: PropTypes.string,
};

DynamicListPageAgGrid.defaultProps = {
    id: undefined,
    getRowClass: undefined,
    locale: undefined,
    dateFormat: undefined,
    thousandSeparator: undefined,
    decimalSeparator: undefined,
    timestampFormatSeconds: 'YYYY-MM-dd HH:mm:ss',
    timestampFormatMinutes: 'YYYY-MM-dd HH:mm',
    currency: '€',
};

export default DynamicListPageAgGrid;
