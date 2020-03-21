import MagicCheckboxInput from './MagicCheckboxInput';
import MagicInputNotImplemented from './MagicInputNotImplemented';
import MagicObjectInput from './MagicObjectInput';
import MagicSelectInput from './MagicSelectInput';
import MagicStringInput from './MagicStringInput';
import MagicTimeStampInput from './MagicTimeStampInput';

const useMagicInput = (type) => {
    switch (type) {
        case 'STRING':
            return MagicStringInput;
        case 'LIST':
            return MagicSelectInput;
        case 'TIME_STAMP':
            return MagicTimeStampInput;
        case 'OBJECT':
            return MagicObjectInput;
        case 'BOOLEAN':
            return MagicCheckboxInput;
        default:
            return MagicInputNotImplemented;
    }
};

export default useMagicInput;
