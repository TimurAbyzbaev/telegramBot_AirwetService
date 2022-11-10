public class User {
    private String nickname;
    private Long userId;

    public String[] newRequest;
    public String[] solveRequest;
    private boolean statusRequest = false;
    private boolean solveRequestFlag = false;
    private boolean newRequestFlag = false;

    public boolean isSolveRequestFlag() {
        return solveRequestFlag;
    }

    public boolean isNewRequestFlag() {
        return newRequestFlag;
    }

    public User(Long userId, String nickname){
        this.userId = userId;
        this.nickname = nickname;
    }

    public void setStatusRequest(){
        statusRequest = true;
    }

    public boolean isStatusRequest() {
        return statusRequest;
    }

    public void setNewRequest(){
        newRequestFlag = true;
        newRequest = new String[6];
        /*
        newRequest[0] - ник пользователя
        newRequest[1] - дата добавления
        newRequest[2] - город
        newRequest[3] - имя объекта
        newRequest[4] - тип проблемы
        newRequest[5] - описание проблемы
         */
    }

    public void setSolveRequest(){
        solveRequestFlag = true;
        solveRequest = new String[3];
        /*
        solveRequest[0] - номер заявки, которую надо закрыть
        solveRequest[1] - что было сделано для решения проблемы
        solveRequest[2] - Дата закрытия заявки
         */
    }

    public String getNickname() {
        return nickname;
    }

    public Long getUserId() {
        return userId;
    }
}
