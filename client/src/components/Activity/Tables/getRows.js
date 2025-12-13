
export default function fetRows(status, teamData, authUser) {
  if (!teamData) return [];
  let userActivity = [];
  teamData.forEach(user => {
    if(user.uid != authUser.uid) return
    user.work.forEach(activity =>{
        if(!activity || activity.status!= status && status!='Total') return 
        userActivity.push(createData(activity));
    });
  });
  userActivity.reverse();
  return userActivity;
}

function createData(doc) {
  const {
    title,
    description,
    category,
    status,
    dateIssued,
    name,
    comments,
    _id,
  } = doc;

  return {
    name,
    title,
    category,
    date: dateIssued ? dateIssued.substring(0, 10) : '',
    status,
    history: description,
    comments,
    _id,
  };
}
