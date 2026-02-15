import React, { useContext } from 'react';
import { makeStyles } from '@material-ui/core/styles';
import InputLabel from '@material-ui/core/InputLabel';
import FormControl from '@material-ui/core/FormControl';
import Select from '@material-ui/core/Select';
import TextField from '@material-ui/core/TextField';
import Autocomplete from '@material-ui/lab/Autocomplete';
import TeamDataContext from '../../../Contexts/TeamDataContext';

const useStyles = makeStyles((theme) => ({
  formControl: {
    margin: theme.spacing(1),
    minWidth: 120,
    display:'flex',
    // border:'1px solid black',
    flexDirection:'row',
  },
  selectEmpty: {
    marginTop: theme.spacing(2),
  },
}));


const getOptions = (teamData) =>{
  const options = [];
  teamData.forEach(user =>{
    options.push(user.name);
  })
  return options;
}
export default function NativeSelects(props) {
  const classes = useStyles();
  const teamData = useContext(TeamDataContext).teamData;
  const options = getOptions(teamData);
  const {user,setUser} = props;
  const {status,setStatus} = props;
  const [inputValue, setInputValue] = React.useState('');

  return (
    <div>
      <FormControl variant="outlined" className={classes.formControl}>
        <div style = {{marginRight:'10px'}}>
          <InputLabel htmlFor="outlined-age-native-simple">Status</InputLabel>
          <Select
            native
            value={status}
            onChange={(e)=>{setStatus(e.target.value)}}
            label="Status"
            inputProps={{
              name: 'status',
              id: 'outlined-age-native-simple',
            }}
          >
            <option aria-label="None" value="" />
            <option value={'Approved'}>Approved</option>
            <option value={'Pending'}>Pending</option>
            <option value={'Denied'}>Denied</option>
          </Select>
        </div>
        {/* ************************************************************************* */}
          <Autocomplete
            value={user}
            onChange={(event, newValue) => {
              setUser(newValue);
            }}
            inputValue={inputValue}
            onInputChange={(event, newInputValue) => {
              setInputValue(newInputValue);
            }}
            id="controllable-states-demo"
            options={options}
            style={{ width: 300 }}
            renderInput={(params) => <TextField {...params} label="Select User" variant="outlined" />}
        />
      </FormControl>
    </div>
  );
}
