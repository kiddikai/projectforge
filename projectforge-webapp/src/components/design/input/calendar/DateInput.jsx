import moment from 'moment';
import 'moment/min/locales';
import PropTypes from 'prop-types';
import React from 'react';
import DayPicker from 'react-day-picker';
import MomentLocaleUtils from 'react-day-picker/moment';
import { connect } from 'react-redux';
import AdvancedPopper from '../../popper/AdvancedPopper';
import AdditionalLabel from '../AdditionalLabel';
import InputContainer from '../InputContainer';
import styles from './CalendarInput.module.scss';

function DateInput(
    {
        additionalLabel,
        date,
        hideDayPicker,
        jsDateFormat,
        label,
        locale,
        noInputContainer,
        setDate,
        todayButton,
    },
) {
    const [inputValue, setInputValue] = React.useState('');
    const [isActive, setIsActive] = React.useState(false);
    const [isOpen, setIsOpen] = React.useState(false);
    const inputRef = React.useRef(null);
    const Tag = noInputContainer ? React.Fragment : InputContainer;

    React.useEffect(() => {
        if (date) {
            setInputValue(moment(date)
                .format(jsDateFormat));
        } else {
            setInputValue('');
        }
    }, [date]);

    const handleBlur = () => {
        setIsActive(false);

        if (inputValue.trim() === '') {
            setDate(undefined);
            return;
        }

        const momentDate = moment(inputValue, jsDateFormat);

        if (momentDate.isValid()) {
            setDate(momentDate.toDate());
        } else {
            setInputValue(moment(date)
                .format(jsDateFormat));
        }
    };

    const handleChange = ({ target }) => {
        setInputValue(target.value);

        // Has to be strict, so moment doesnt correct your input every time you time
        const momentDate = moment(target.value, jsDateFormat, true);

        if (momentDate.isValid()) {
            setDate(momentDate.toDate());
        }
    };

    const handleFocus = () => setIsActive(true);

    const handleKeyDown = (event) => {
        const momentDate = moment(inputValue, jsDateFormat, true);

        if (momentDate.isValid()) {
            let newDate;
            if (event.key === 'ArrowUp') {
                newDate = momentDate.add(1, 'd');
            } else if (event.key === 'ArrowDown') {
                newDate = momentDate.subtract(1, 'd');
            }

            if (newDate) {
                event.preventDefault();
                setDate(newDate.toDate());
            }
        }
    };

    const handleTagClick = () => {
        if (inputRef.current) {
            inputRef.current.focus();
        }
    };

    const tagProps = {};

    if (Tag !== React.Fragment) {
        tagProps.onClick = handleTagClick;
        tagProps.isActive = isActive || inputValue !== '';
        tagProps.label = label;
    }

    const placeholder = jsDateFormat
        .split('')
        .filter((char, index) => index >= inputValue.length)
        .join('');

    const input = (
        <React.Fragment>
            <Tag {...tagProps}>
                <div className={styles.dateInput}>
                    {isActive && (
                        <span
                            className={styles.placeholder}
                            style={{ left: `${jsDateFormat.length - placeholder.length}ch` }}
                        >
                        {placeholder}
                    </span>
                    )}
                    <input
                        ref={inputRef}
                        onBlur={handleBlur}
                        onChange={handleChange}
                        onFocus={handleFocus}
                        onKeyDown={handleKeyDown}
                        size={jsDateFormat.length}
                        type="text"
                        value={inputValue}
                    />
                </div>
            </Tag>
            <AdditionalLabel title={additionalLabel} />
        </React.Fragment>
    );

    if (hideDayPicker) {
        return input;
    }

    const handleDayPickerClick = (day, { selected }) => setDate(selected ? undefined : day);

    return (
        <AdvancedPopper
            basic={input}
            setIsOpen={setIsOpen}
            isOpen={isOpen}
            withInput
        >
            <DayPicker
                selectedDays={date}
                onDayClick={handleDayPickerClick}
                month={date}
                locale={locale}
                localeUtils={MomentLocaleUtils}
                onTodayButtonClick={setDate}
                todayButton={todayButton}
            />
        </AdvancedPopper>
    );
}

DateInput.propTypes = {
    jsDateFormat: PropTypes.string.isRequired,
    setDate: PropTypes.func.isRequired,
    additionalLabel: PropTypes.string,
    date: PropTypes.instanceOf(Date),
    hideDayPicker: PropTypes.bool,
    label: PropTypes.string,
    locale: PropTypes.string,
    noInputContainer: PropTypes.bool,
    todayButton: PropTypes.string,
};

DateInput.defaultProps = {
    additionalLabel: undefined,
    date: undefined,
    hideDayPicker: false,
    label: undefined,
    locale: 'en',
    noInputContainer: false,
    todayButton: undefined,
};

const mapStateToProps = ({ authentication }) => ({
    jsDateFormat: authentication.user.jsDateFormat,
    locale: authentication.user.locale,
});

export default connect(mapStateToProps)(DateInput);
