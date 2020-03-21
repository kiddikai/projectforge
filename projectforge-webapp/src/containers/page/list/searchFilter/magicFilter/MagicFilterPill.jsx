import { faBan } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import classNames from 'classnames';
import PropTypes from 'prop-types';
import React from 'react';
import { connect } from 'react-redux';
import { removeFilter, setFilter } from '../../../../../actions/list/filter';
import AdvancedPopper from '../../../../../components/design/popper/AdvancedPopper';
import AdvancedPopperAction from '../../../../../components/design/popper/AdvancedPopperAction';
import styles from '../../ListPage.module.scss';
import useMagicInput from './inputs/MagicInputHook';

function MagicFilterPill(
    {
        children,
        id,
        isNew,
        isRemovable,
        label,
        onFilterDelete,
        onFilterSet,
        translations,
        filterType,
        value,
        ...props
    },
) {
    const MagicInput = useMagicInput(filterType);

    const [isOpen, setIsOpen] = React.useState(isNew);
    const [tempValue, setTempValue] = React.useState(() => {
        console.log(value, MagicInput.defaultValue);
        if (Object.isEmpty(value) && MagicInput.defaultValue !== undefined) {
            return MagicInput.defaultValue;
        }

        return value;
    });

    const handleCancel = () => {
        setTempValue(value);
        setIsOpen(false);
    };

    const handleDelete = () => onFilterDelete(id);

    const handleSave = () => {
        if (MagicInput.isEmpty(tempValue)) {
            handleDelete();
            return;
        }

        onFilterSet(id, tempValue);
        setIsOpen(false);
    };

    return (
        <div className={styles.magicFilter}>
            <AdvancedPopper
                setIsOpen={setIsOpen}
                isOpen={isOpen}
                basic={(
                    <React.Fragment>
                        {value && Object.keys(value).length
                            ? MagicInput.getLabel(label, value, props)
                            : label}
                        {isRemovable && (
                            <FontAwesomeIcon
                                icon={faBan}
                                className={styles.deleteIcon}
                                onClick={() => onFilterDelete(id)}
                            />
                        )}
                    </React.Fragment>
                )}
                contentClassName={classNames(
                    styles.pill,
                    { [styles.marked]: isOpen || !Object.isEmpty(value) },
                )}
                actions={(
                    <React.Fragment>
                        <AdvancedPopperAction
                            type="delete"
                            disabled={!value}
                            onClick={handleDelete}
                        >
                            {translations.delete || ''}
                        </AdvancedPopperAction>
                        <AdvancedPopperAction
                            type="success"
                            onClick={handleSave}
                        >
                            {translations.save || ''}
                        </AdvancedPopperAction>
                    </React.Fragment>
                )}
            >
                <p className={styles.title}>{label}</p>
                <div className={styles.content}>
                    <MagicInput
                        label={label}
                        id={id}
                        onChange={setTempValue}
                        onSubmit={handleSave}
                        onCancel={handleCancel}
                        value={tempValue}
                        translations={translations}
                        {...props}
                    />
                </div>
            </AdvancedPopper>
        </div>
    );
}

MagicFilterPill.propTypes = {
    id: PropTypes.string.isRequired,
    label: PropTypes.string.isRequired,
    onFilterDelete: PropTypes.func.isRequired,
    onFilterSet: PropTypes.func.isRequired,
    translations: PropTypes.shape({}).isRequired,
    children: PropTypes.node,
    isNew: PropTypes.bool,
    isRemovable: PropTypes.bool,
    filterType: PropTypes.string,
    value: PropTypes.shape({}),
};

MagicFilterPill.defaultProps = {
    children: undefined,
    isNew: false,
    isRemovable: false,
    filterType: undefined,
    value: {},
};

const mapStateToProps = ({ list }) => ({
    translations: list.categories[list.currentCategory].ui.translations,
});

const actions = dispatch => ({
    onFilterDelete: fieldId => dispatch(removeFilter(fieldId)),
    onFilterSet: (fieldId, newValue) => dispatch(setFilter(fieldId, newValue)),
});

export default connect(mapStateToProps, actions)(MagicFilterPill);
